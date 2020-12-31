package com.reucon.openfire.plugins.outreach.correct;

import com.reucon.openfire.plugins.outreach.OutreachPlugin;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.disco.UserFeaturesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Iterator;

/**
 * Encapsulates support for message correct queries.
 */
public class MessageCorrectSupport implements UserFeaturesProvider {

    protected final XMPPServer server;
    protected IQMessageCorrectHandler iqHandler;
    private static final String NAMESPACE = "jabber:iq:msg-correct";
    private static final Logger Log = LoggerFactory.getLogger(OutreachPlugin.class);

    public MessageCorrectSupport(XMPPServer server) {

        this.server = server;
        this.iqHandler = new IQMessageCorrectHandler();
    }

    public void start() {
        try {
            iqHandler.initialize(server);
            iqHandler.start();
            server.getIQRouter().addHandler(iqHandler);
        } catch (Exception e) {
            Log.error("Unable to initialize and start " + iqHandler.getClass());
        }
    }

    public void stop() {
        try {
            IQRouter iqRouter = server.getIQRouter();
            iqRouter.removeHandler(iqHandler);
            iqHandler.stop();
            iqHandler.destroy();
        } catch (Exception e) {
            Log.warn("Unable to stop and destroy " + iqHandler.getClass());
        }
    }

    @Override
    public Iterator<String> getFeatures()
    {
        return Collections.singleton(NAMESPACE).iterator();
    }
}
