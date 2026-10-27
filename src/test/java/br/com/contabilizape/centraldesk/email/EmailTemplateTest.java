package br.com.contabilizape.centraldesk.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.ClassPathResource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assuntos fixos, arquivos existentes e a marcação de e-mails confidenciais (códigos e segurança da conta).
 */
class EmailTemplateTest {

    @Test
    void ticketSubjectsAreExactlyTheAgreedOnes() {
        assertThat(EmailTemplate.TICKET_OPENED_REQUESTER.subject(7L)).isEqualTo("Chamado Aberto");
        assertThat(EmailTemplate.TICKET_OPENED_DEPARTMENT.subject(7L)).isEqualTo("Chamado Aberto");
        assertThat(EmailTemplate.TICKET_IN_PROGRESS.subject(7L)).isEqualTo("Chamado em andamento");
        assertThat(EmailTemplate.TICKET_REASSIGNED_REQUESTER.subject(7L)).isEqualTo("Chamado reatribuído");
        assertThat(EmailTemplate.TICKET_REASSIGNED_NEW_ASSIGNEE.subject(7L)).isEqualTo("Chamado reatribuído");
        assertThat(EmailTemplate.TICKET_REASSIGNED_PREVIOUS_ASSIGNEE.subject(7L)).isEqualTo("Chamado reatribuído");
        assertThat(EmailTemplate.TICKET_NEW_MESSAGE.subject(123L)).isEqualTo("Nova mensagem no chamado #123");
        assertThat(EmailTemplate.TICKET_CLOSED.subject(7L)).isEqualTo("Chamado Encerrado");
        assertThat(EmailTemplate.TICKET_REOPENED.subject(7L)).isEqualTo("Chamado reaberto");
    }

    @Test
    void onlyTheNewMessageSubjectCarriesTheTicketNumber() {
        Arrays.stream(EmailTemplate.values())
                .filter(template -> template != EmailTemplate.TICKET_NEW_MESSAGE)
                .forEach(template -> assertThat(template.subject(98765L)).doesNotContain("98765"));
    }

    @Test
    void securityTemplatesAreConfidentialAndTicketTemplatesAreNot() {
        Arrays.stream(EmailTemplate.values()).forEach(template ->
                assertThat(template.isConfidential()).as(template.name())
                        .isEqualTo(!template.file().startsWith("ticket-")));
    }

    @Test
    void codeTemplatesRequireTheCodeAndItsValidity() {
        for (EmailTemplate template : new EmailTemplate[]{EmailTemplate.EMAIL_VERIFICATION_CODE, EmailTemplate.LOGIN_CODE,
                EmailTemplate.PASSWORD_RESET_CODE, EmailTemplate.PASSWORD_CHANGE_CODE,
                EmailTemplate.TWO_FACTOR_DISABLE_CODE}) {
            assertThat(template.variables()).contains("code", "validityMinutes", "recipientName");
        }
    }

    @Test
    void ticketTemplatesRequireTheCommonHeader() {
        assertThat(EmailTemplate.TICKET_CLOSED.variables()).containsAll(EmailTemplate.TICKET_HEADER);
        assertThat(EmailTemplate.LOGIN_CODE.variables()).doesNotContain("ticketNumber");
    }

    @ParameterizedTest
    @EnumSource(EmailTemplate.class)
    void everyTemplateFileExists(EmailTemplate template) {
        assertThat(new ClassPathResource("templates/" + template.view() + ".html").exists()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(EmailTemplate.class)
    void tagsOnlyUseCharactersResendAccepts(EmailTemplate template) {
        assertThat(template.tag()).matches("[A-Za-z0-9_-]+");
    }
}
