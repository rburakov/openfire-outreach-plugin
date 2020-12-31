package com.reucon.openfire.plugins.outreach;

import com.reucon.openfire.plugins.outreach.archive.MessageArchivePersistenceManager;
import com.reucon.openfire.plugins.outreach.archive.MessageArchiveSupport;
import com.reucon.openfire.plugins.outreach.correct.MessageCorrectPersistenceManager;
import com.reucon.openfire.plugins.outreach.correct.MessageCorrectSupport;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Packet;

import java.io.File;

/**
 * Outreach plugin for Openfire.
 */
public class OutreachPlugin implements Plugin, PacketInterceptor
{
    private InterceptorManager interceptorManager;
    private MessageArchivePersistenceManager messageArchivePersistenceManager;
    private MessageCorrectPersistenceManager messageCorrectPersistenceManager;
    private static final Logger Log = LoggerFactory.getLogger(OutreachPlugin.class);
    private static OutreachPlugin instance;
    public static final String NAME = "outreach";

    private MessageArchiveSupport messageArchiveSupport;
    private MessageCorrectSupport messageCorrectSupport;

    public OutreachPlugin() {
        instance = this;
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory)
    {
        messageArchivePersistenceManager = new MessageArchivePersistenceManager();
        messageCorrectPersistenceManager = new MessageCorrectPersistenceManager();
        interceptorManager = InterceptorManager.getInstance();

        // register with interceptor manager
        interceptorManager.addInterceptor(this);

        messageArchiveSupport = new MessageArchiveSupport(XMPPServer.getInstance());
        messageArchiveSupport.start();

        messageCorrectSupport = new MessageCorrectSupport(XMPPServer.getInstance());
        messageCorrectSupport.start();
    }

    public static OutreachPlugin getInstance() {
        return instance;
    }

    public void destroyPlugin()
    {
        interceptorManager.removeInterceptor(this);
        messageArchiveSupport.stop();
        messageCorrectSupport.stop();
        instance = null;
    }

    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) throws PacketRejectedException {

    }

    public MessageArchivePersistenceManager getMessageArchivePersistenceManager() {
        return messageArchivePersistenceManager;
    }

    public MessageCorrectPersistenceManager getMessageCorrectPersistenceManager() {
        return messageCorrectPersistenceManager;
    }
}
