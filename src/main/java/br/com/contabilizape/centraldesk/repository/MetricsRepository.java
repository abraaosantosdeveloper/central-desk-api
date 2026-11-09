package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.dto.MetricsResponse.AssigneeStat;
import br.com.contabilizape.centraldesk.dto.MetricsResponse.GroupStat;
import br.com.contabilizape.centraldesk.dto.MetricsResponse.Live;
import br.com.contabilizape.centraldesk.dto.MetricsResponse.OldestTicket;
import br.com.contabilizape.centraldesk.dto.MetricsResponse.PriorityShare;
import br.com.contabilizape.centraldesk.dto.MetricsResponse.Summary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agregações do painel de métricas (SCRUM-216). SQL nativo porque médias de intervalos e agrupamento por dia no
 * fuso da empresa não cabem bem em JPQL. Só leitura: nada aqui altera chamado.
 *
 * Os chamados do período são os abertos nele (created_at, janela [from, to)). Os filtros de setor de origem,
 * categoria e prioridade ({@link Scope}) valem para todas as consultas.
 */
@Repository
public class MetricsRepository {

    private static final int OLDEST_LIMIT = 5;

    private final NamedParameterJdbcTemplate jdbc;
    private final String zone;

    public MetricsRepository(JdbcTemplate jdbcTemplate, @Value("${app.tickets.time-zone}") ZoneId zone) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.zone = zone.getId();
    }

    /** Filtros já resolvidos pelo serviço (o escopo do coordenador entra como origem). Campo nulo = sem filtro. */
    public record Scope(UUID originDepartmentId, UUID categoryId, Short priorityId) {
    }

    /** Janela de tempo [from, to): to é exclusivo (o dia seguinte ao último dia do período, à meia-noite). */
    public record Window(OffsetDateTime from, OffsetDateTime to) {
    }

    public Summary summary(Scope scope, Window window) {
        MapSqlParameterSource params = windowParams(window);
        String where = scopeSql(scope, params);
        return jdbc.queryForObject("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE t.first_assigned_at IS NOT NULL) AS taken,
                       count(*) FILTER (WHERE t.status = 'CLOSED') AS closed,
                       avg(extract(epoch FROM (t.first_assigned_at - t.created_at))) AS avg_response,
                       avg(extract(epoch FROM (t.closed_at - t.created_at))) AS avg_resolution,
                       (SELECT count(*)
                          FROM ticket_closures c JOIN tickets t ON t.id = c.ticket_id
                         WHERE c.reopened_at >= :from AND c.reopened_at < :to %1$s) AS reopened
                  FROM tickets t
                 WHERE t.created_at >= :from AND t.created_at < :to %1$s
                """.formatted(where), params, (rs, row) -> new Summary(
                rs.getLong("total"), rs.getLong("taken"), rs.getLong("closed"), rs.getLong("reopened"),
                seconds(rs, "avg_response"), seconds(rs, "avg_resolution")));
    }

    /** Abertos e em andamento agora, sem recorte de datas. */
    public Live live(Scope scope) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = scopeSql(scope, params);
        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE t.status IN ('OPEN', 'IN_PROGRESS')) AS open,
                       count(*) FILTER (WHERE t.status = 'OPEN') AS unassigned
                  FROM tickets t
                 WHERE true %s
                """.formatted(where), params, (rs, row) -> new Live(rs.getLong("open"), rs.getLong("unassigned")));
    }

    /** Chamados abertos por dia no fuso da empresa; dias sem movimento não aparecem (o serviço completa). */
    public Map<LocalDate, Long> openedPerDay(Scope scope, Window window) {
        return perDay("t.created_at", scope, window);
    }

    /** Encerrados por dia (closed_at atual: um chamado reaberto sai da conta até ser encerrado de novo). */
    public Map<LocalDate, Long> closedPerDay(Scope scope, Window window) {
        return perDay("t.closed_at", scope, window);
    }

    private Map<LocalDate, Long> perDay(String column, Scope scope, Window window) {
        MapSqlParameterSource params = windowParams(window);
        String where = scopeSql(scope, params);
        Map<LocalDate, Long> days = new HashMap<>();
        jdbc.query("""
                SELECT (%1$s AT TIME ZONE :zone)::date AS day, count(*) AS total
                  FROM tickets t
                 WHERE %1$s >= :from AND %1$s < :to %2$s
                 GROUP BY 1
                """.formatted(column, where), params, rs -> {
            days.put(rs.getDate("day").toLocalDate(), rs.getLong("total"));
        });
        return days;
    }

    /** Todas as prioridades, inclusive as sem chamado, da mais baixa para a mais alta. */
    public List<PriorityShare> byPriority(Scope scope, Window window) {
        MapSqlParameterSource params = windowParams(window);
        String where = scopeSql(scope, params);
        return jdbc.query("""
                SELECT p.id, p.label, count(t.id) AS total
                  FROM priorities p
                  LEFT JOIN tickets t ON t.priority_id = p.id
                       AND t.created_at >= :from AND t.created_at < :to %s
                 GROUP BY p.id, p.label
                 ORDER BY p.id
                """.formatted(where), params,
                (rs, row) -> new PriorityShare(rs.getShort("id"), rs.getString("label"), rs.getLong("total")));
    }

    /** Só categorias com chamado no período, as mais frequentes primeiro. */
    public List<GroupStat> byCategory(Scope scope, Window window) {
        return groupStats("categories", "t.category_id", "label", scope, window);
    }

    /** Só setores com chamado no período, os mais frequentes primeiro. */
    public List<GroupStat> byOriginDepartment(Scope scope, Window window) {
        return groupStats("departments", "t.origin_department_id", "name", scope, window);
    }

    private List<GroupStat> groupStats(String table, String foreignKey, String labelColumn, Scope scope, Window window) {
        MapSqlParameterSource params = windowParams(window);
        String where = scopeSql(scope, params);
        return jdbc.query("""
                SELECT g.id, g.%2$s AS label, count(*) AS total,
                       avg(extract(epoch FROM (t.closed_at - t.created_at))) AS avg_resolution
                  FROM tickets t JOIN %1$s g ON g.id = %3$s
                 WHERE t.created_at >= :from AND t.created_at < :to %4$s
                 GROUP BY g.id, g.%2$s
                 ORDER BY total DESC, label
                """.formatted(table, labelColumn, foreignKey, where), params,
                (rs, row) -> new GroupStat(rs.getObject("id", UUID.class), rs.getString("label"),
                        rs.getLong("total"), seconds(rs, "avg_resolution")));
    }

    /** Desempenho de quem é responsável por chamados abertos no período (o responsável atual de cada um). */
    public List<AssigneeStat> byAssignee(Scope scope, Window window) {
        MapSqlParameterSource params = windowParams(window);
        String where = scopeSql(scope, params);
        return jdbc.query("""
                SELECT u.id, u.full_name,
                       count(*) FILTER (WHERE t.status = 'IN_PROGRESS') AS in_progress,
                       count(*) FILTER (WHERE t.first_assigned_at IS NOT NULL) AS taken,
                       count(*) FILTER (WHERE t.status = 'CLOSED') AS closed,
                       avg(extract(epoch FROM (t.first_assigned_at - t.created_at))) AS avg_response,
                       avg(extract(epoch FROM (t.closed_at - t.created_at))) AS avg_resolution
                  FROM tickets t JOIN users u ON u.id = t.assignee_id
                 WHERE t.created_at >= :from AND t.created_at < :to %s
                 GROUP BY u.id, u.full_name
                 ORDER BY taken DESC, u.full_name
                """.formatted(where), params,
                (rs, row) -> new AssigneeStat(rs.getObject("id", UUID.class), rs.getString("full_name"),
                        rs.getLong("in_progress"), rs.getLong("taken"), rs.getLong("closed"),
                        seconds(rs, "avg_response"), seconds(rs, "avg_resolution")));
    }

    /** Os mais antigos da fila agora (sem responsável), sem recorte de datas. */
    public List<OldestTicket> oldestUnassigned(Scope scope) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", OLDEST_LIMIT);
        String where = scopeSql(scope, params);
        return jdbc.query("""
                SELECT t.id, t.ticket_number, t.title, r.full_name AS requester, d.name AS department, t.created_at
                  FROM tickets t
                  JOIN users r ON r.id = t.requester_id
                  JOIN departments d ON d.id = t.origin_department_id
                 WHERE t.status = 'OPEN' %s
                 ORDER BY t.created_at, t.ticket_number
                 LIMIT :limit
                """.formatted(where), params,
                (rs, row) -> new OldestTicket(rs.getObject("id", UUID.class), rs.getLong("ticket_number"),
                        rs.getString("title"), rs.getString("requester"), rs.getString("department"),
                        rs.getObject("created_at", OffsetDateTime.class)));
    }

    private MapSqlParameterSource windowParams(Window window) {
        return new MapSqlParameterSource()
                .addValue("from", window.from())
                .addValue("to", window.to())
                .addValue("zone", zone);
    }

    // Condições só entram quando o filtro existe: evita parâmetro nulo sem tipo no PostgreSQL
    private static String scopeSql(Scope scope, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder();
        if (scope.originDepartmentId() != null) {
            sql.append(" AND t.origin_department_id = :origin");
            params.addValue("origin", scope.originDepartmentId());
        }
        if (scope.categoryId() != null) {
            sql.append(" AND t.category_id = :category");
            params.addValue("category", scope.categoryId());
        }
        if (scope.priorityId() != null) {
            sql.append(" AND t.priority_id = :priority");
            params.addValue("priority", scope.priorityId());
        }
        return sql.toString();
    }

    private static Long seconds(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : value.setScale(0, RoundingMode.HALF_UP).longValue();
    }
}
