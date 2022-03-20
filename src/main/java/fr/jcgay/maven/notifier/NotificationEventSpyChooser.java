package fr.jcgay.maven.notifier;

import com.google.common.annotations.VisibleForTesting;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.MavenExecutionResult;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

import javax.annotation.Nullable;
import java.util.List;

@Component(role = EventSpy.class, hint = "notification", description = "Send notification to indicate build status.")
public class NotificationEventSpyChooser extends AbstractEventSpy {

    public static final String SKIP_NOTIFICATION = "skipNotification";

    @Requirement
    private Logger logger;

    @Requirement
    private List<Notifier> availableNotifiers;

    @Requirement
    private ConfigurationParser configurationParser;

    private Notifier activeNotifier;

    @Override
    public void init(Context context) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("Using maven-notifier " + Version.current().get());
        }
        Configuration configuration = configurationParser.get();
        context.getData().put("notifier.configuration", configuration);

        chooseNotifier(configuration);
        activeNotifier.init(context);
    }

    @Override
    public void onEvent(Object event) throws Exception {
        if (shouldSendNotification()) {
            if (isExecutionResult(event) && hasFailedWithoutProject((MavenExecutionResult) event)) {
                activeNotifier.onFailWithoutProject(((MavenExecutionResult) event).getExceptions());
            } else if (isExecutionResult(event)) {
                activeNotifier.onEvent((MavenExecutionResult) event);
            }
        }
    }

    private boolean hasFailedWithoutProject(MavenExecutionResult event) {
        return event.getProject() == null && event.hasExceptions();
    }

    private boolean shouldSendNotification() {
        return !"true".equalsIgnoreCase(System.getProperty(SKIP_NOTIFICATION));
    }

    @Override
    public void close() throws Exception {
        activeNotifier.close();
    }

    private boolean isExecutionResult(Object event) {
        return event instanceof MavenExecutionResult;
    }

    protected void chooseNotifier(Configuration configuration) {
        logger.debug("Choosing notifier...");

        String implementation =
            configuration.hasImplementation() ?
                configuration.getImplementation() :
                configuration.getDefaultImplementation();

        activeNotifier = findNotifier(availableNotifiers, implementation);

        if (activeNotifier == null) {
            logger.debug("Using default notifier");
            activeNotifier = UselessNotifier.EMPTY;
        }
    }

    @Nullable
    protected Notifier findNotifier(List<Notifier> notifiers, String implementation) {
        return notifiers.stream()
            .filter(notifier -> notifier.isCandidateFor(implementation))
            .findFirst()
            .orElse(null);
    }

    @VisibleForTesting
    void setAvailableNotifiers(List<Notifier> availableNotifiers) {
        this.availableNotifiers = availableNotifiers;
    }

    @VisibleForTesting
    void setConfigurationParser(ConfigurationParser configurationParser) {
        this.configurationParser = configurationParser;
    }
}
