package fr.jcgay.maven.notifier

import org.apache.maven.execution.DefaultMavenExecutionResult
import org.apache.maven.execution.MavenExecutionResult
import org.codehaus.plexus.logging.Logger
import org.mockito.ArgumentCaptor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import javax.annotation.Nullable

import static fr.jcgay.maven.notifier.NotificationEventSpyChooser.SKIP_NOTIFICATION
import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.entry
import static org.mockito.Matchers.any
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.never
import static org.mockito.Mockito.verify
import static org.mockito.Mockito.when

class NotificationEventSpyChooserTest {

    @InjectMocks
    private NotificationEventSpyChooser chooser
    @InjectMocks
    private NotificationEventSpyChooser emptyChooser

    @Mock
    private Notifier notifier
    @Mock
    private Notifier unexpectedNotifier
    @Mock
    private MavenExecutionResult anEvent
    @Mock
    private Logger logger

    private Configuration configuration
    private Configuration emptyConfiguration

    @BeforeMethod
    void setUp() throws Exception {
        def configurationParser = mock(ConfigurationParser.class)
        def emptyConfigurationParser = mock(ConfigurationParser.class)

        configuration = createConfigurationWithImplementation()
        emptyConfiguration = createConfigurationWithoutImplementation()

        when configurationParser.get() thenReturn this.configuration
        when emptyConfigurationParser.get() thenReturn this.emptyConfiguration

        chooser = createChooser(configurationParser, notifier)
        emptyChooser = createChooser(emptyConfigurationParser, null)
        MockitoAnnotations.initMocks(this)

        System.setProperty(SKIP_NOTIFICATION, String.valueOf(false))

        when unexpectedNotifier.isCandidateFor("anything") thenReturn false

        when notifier.isCandidateFor("anything") thenReturn true
        chooser.availableNotifiers = [notifier]
    }

    Configuration createConfigurationWithImplementation() {
        Configuration configuration = createConfigurationWithoutImplementation()
        configuration.setImplementation("anything")
        return configuration
    }

    Configuration createConfigurationWithoutImplementation() {
        return new Configuration()
    }

    NotificationEventSpyChooser createChooser(ConfigurationParser configurationParser, @Nullable Notifier notifier) {
        NotificationEventSpyChooser chooser = new NotificationEventSpyChooser()
        chooser.setConfigurationParser(configurationParser)
        chooser.availableNotifiers = notifier ? [notifier] : []

        return chooser
    }

    @Test
    void 'should not notify if event is not a build result'() throws Exception {
        chooser.init({ [:] })
        chooser.onEvent('this is not a build result')
        chooser.close()

        verify(notifier, never()).onEvent(any(MavenExecutionResult))
    }

    @Test
    void 'should not notify when property skipNotification is true'() throws Exception {
        System.setProperty(SKIP_NOTIFICATION, String.valueOf(true))

        chooser.init({ [:] })
        chooser.onEvent(anEvent)
        chooser.close()

        verify(notifier, never()).onEvent(any(MavenExecutionResult))
    }

    @Test
    void 'should notify when property skipNotification is false'() throws Exception {
        System.setProperty(SKIP_NOTIFICATION, String.valueOf(false))

        chooser.init({ [:] })
        chooser.onEvent(anEvent)
        chooser.close()

        verify(notifier).onEvent(anEvent)
    }

    @Test
    void 'should notify failure when build fails without project'() throws Exception {
        DefaultMavenExecutionResult event = new DefaultMavenExecutionResult()
        event.project = null
        event.addException(new NullPointerException())

        chooser.init({ [:] })
        chooser.onEvent(event)
        chooser.close()

        verify(notifier).onFailWithoutProject(event.getExceptions())
        verify(notifier, never()).onEvent(event)
    }

    @Test
    void 'should send notification with configured notifier'() throws Exception {
        chooser.availableNotifiers = [unexpectedNotifier, notifier]

        chooser.init({ [:] })
        chooser.onEvent(anEvent)
        chooser.close()

        verify(notifier).onEvent(anEvent)
    }

    @Test
    void 'should not fail when no notifier is configured'() throws Exception {
        chooser.availableNotifiers = [unexpectedNotifier]

        chooser.init({ [:] })
        chooser.onEvent(anEvent)
        chooser.close()

        verify(unexpectedNotifier, never()).onEvent(any(MavenExecutionResult))
    }

    @Test
    void 'should close notifier'() throws Exception {
        chooser.init({ [:] })
        chooser.onEvent(anEvent)
        chooser.close()

        verify(notifier).close()
    }

    @Test
    void 'should forward configuration to notifier'() {
        def contextCaptor = ArgumentCaptor.forClass(FakeContext)

        chooser.init(new FakeContext())

        verify(notifier).init(contextCaptor.capture())
        assertThat(contextCaptor.value.data).contains(entry('notifier.configuration', configuration))
    }

    @Test
    void 'should use default notifier when implementation is not specified'() {
        emptyChooser.chooseNotifier(emptyConfiguration)
        emptyChooser.activeNotifier.isCandidateFor(configuration.defaultImplementation)
    }

}
