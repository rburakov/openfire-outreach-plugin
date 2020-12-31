package com.reucon.openfire.plugins.outreach.archive;

import com.reucon.openfire.plugins.outreach.OutreachPlugin;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.disco.UserFeaturesProvider;
import org.jivesoftware.openfire.muc.MultiUserChatManager;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Encapsulates support for message archive queries.
 */
public class MessageArchiveSupport implements UserFeaturesProvider {

    protected final XMPPServer server;
    protected IQMessageArchiveHandler iqHandler;
    private static final String NAMESPACE = "urn:xmpp:mam:2a";
    private static final Logger Log = LoggerFactory.getLogger(OutreachPlugin.class);

    public MessageArchiveSupport(XMPPServer server) {

        this.server = server;
        this.iqHandler = new IQMessageArchiveHandler();
    }

    public void start() {
        try {
            iqHandler.initialize(server);
            iqHandler.start();
        } catch (Exception e) {
            Log.error("Unable to initialize and start " + iqHandler.getClass());
        }

        MultiUserChatManager manager = server.getMultiUserChatManager();
        for (MultiUserChatService mucService : manager.getMultiUserChatServices()) {
            mucService.addIQHandler(iqHandler);
            mucService.addExtraFeature(NAMESPACE);
        }
        server.getIQRouter().addHandler(iqHandler);
    }

    public void stop() {
        IQRouter iqRouter = server.getIQRouter();
        try {
            iqHandler.stop();
            iqHandler.destroy();
        } catch (Exception e) {
            Log.warn("Unable to stop and destroy " + iqHandler.getClass());
        }

        MultiUserChatManager manager = server.getMultiUserChatManager();
        for (MultiUserChatService mucService : manager.getMultiUserChatServices()) {
            mucService.removeIQHandler(iqHandler);
            mucService.removeExtraFeature(NAMESPACE);
        }
        if (iqRouter != null) {
            iqRouter.removeHandler(iqHandler);
        }
    }

    @Override
    public Iterator<String> getFeatures()
    {
        return Collections.singleton(NAMESPACE).iterator();
    }
}
