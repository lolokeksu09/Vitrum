package app.rayclient.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class TvNavigationModelTest {
    @Test public void noVpnEngineIsReported() {
        TvNavigationModel model = new TvNavigationModel();
        assertEquals(TvNavigationModel.Page.HOME, model.current());
        assertFalse("UI shell must not claim connection support", model.canConnect());
    }

    @Test public void backFromEverySubPageReturnsHome() {
        TvNavigationModel model = new TvNavigationModel();
        for (TvNavigationModel.Page page : TvNavigationModel.Page.values()) {
            if (page == TvNavigationModel.Page.HOME) continue;
            model.open(page);
            assertTrue(model.backToHome());
            assertEquals(TvNavigationModel.Page.HOME, model.current());
        }
        assertFalse("Back at Home must return to the system", model.backToHome());
    }

    @Test public void savedNavigationIsValidatedAndRestored() {
        TvNavigationModel model = new TvNavigationModel();
        model.open(TvNavigationModel.Page.APPS);
        model.restore("SERVERS");
        assertEquals(TvNavigationModel.Page.SERVERS, model.current());
        model.restore("UNKNOWN");
        assertEquals(TvNavigationModel.Page.HOME, model.current());
        model.restore(null);
        assertEquals(TvNavigationModel.Page.HOME, model.current());
    }

    @Test(expected=IllegalArgumentException.class)
    public void nullDestinationsAreRejected() {
        new TvNavigationModel().open(null);
    }
}
