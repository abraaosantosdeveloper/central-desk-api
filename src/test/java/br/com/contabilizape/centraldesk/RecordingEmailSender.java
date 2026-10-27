package br.com.contabilizape.centraldesk;

import br.com.contabilizape.centraldesk.email.EmailMessage;
import br.com.contabilizape.centraldesk.email.EmailSender;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guarda os e-mails "enviados" nos testes de integração, para conferir destinatários, assuntos e ler
 * os códigos de verificação (o template põe o código sozinho num parágrafo).
 */
public class RecordingEmailSender implements EmailSender {

    private static final Pattern CODE = Pattern.compile(">(\\d{6})</p>");

    private final List<EmailMessage> messages = new CopyOnWriteArrayList<>();

    @Override
    public void send(EmailMessage message) {
        messages.add(message);
    }

    public List<EmailMessage> messages() {
        return List.copyOf(messages);
    }

    public List<EmailMessage> to(String email) {
        return messages.stream().filter(message -> message.to().equals(email)).toList();
    }

    public List<String> subjectsTo(String email) {
        return to(email).stream().map(EmailMessage::subject).toList();
    }

    /** Código do último e-mail com código mandado para o endereço. */
    public String lastCodeTo(String email) {
        List<EmailMessage> received = to(email);
        for (int i = received.size() - 1; i >= 0; i--) {
            Matcher matcher = CODE.matcher(received.get(i).html());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new AssertionError("Nenhum código enviado para " + email + "; e-mails: " + subjectsTo(email));
    }

    public void clear() {
        messages.clear();
    }
}
