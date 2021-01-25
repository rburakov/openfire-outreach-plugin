package com.reucon.openfire.plugins.outreach.correct;

import com.reucon.openfire.plugins.outreach.OutreachPlugin;
import org.dom4j.Element;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatManager;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError.Condition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

public class IQMessageCorrectHandler extends IQHandler implements ServerFeaturesProvider {

    private static final Logger Log = LoggerFactory.getLogger(OutreachPlugin.class);

    private static final String MODULE_NAME = "XMPP Message Correct Handler";
    private static final String NAMESPACE = "jabber:iq:msg-correct";
    private final IQHandlerInfo info = new IQHandlerInfo("query", NAMESPACE);
    private MessageCorrectPersistenceManager messageCorrectPersistenceManager;

    public IQMessageCorrectHandler() {
        super(MODULE_NAME);
    }

    public IQ handleIQ(IQ packet) throws UnauthorizedException {

        Element queryElement = packet.getChildElement();
        String messageStanzaId = queryElement.attributeValue("sid");
        String messageAction = queryElement.attributeValue("ma");
        Long ts = null;

        Log.debug("Processing " + packet.getType() + " request from " + packet.getFrom().toString() + " messageId " + messageStanzaId);

        IQ reply = IQ.createResultIQ(packet);
        Element query = reply.setChildElement("query", NAMESPACE);

        if (packet.getFrom() != null && packet.getType() != null && packet.getType().toString().equals("set") && messageStanzaId != null && messageAction != null) {

            String fromJID = packet.getFrom().toBareJID();

            //get message
            MessageCorrectPersistenceManager.GetMessageResult rm = this.messageCorrectPersistenceManager.getMessage(fromJID, messageStanzaId);
            if (rm != null){

                //case delete
                if (messageAction.equals("delete")){
                    ts = this.messageCorrectPersistenceManager.deleteMessage(rm.id);
                }

                //case edit
                else if (messageAction.equals("edit")){
                    Element bodyElement = queryElement.element("body");
                    if (bodyElement != null) {
                        String messageBody = bodyElement.getText();
                        if (messageBody.length() > 0) {
                            ts = this.messageCorrectPersistenceManager.editMessage(rm.id, rm.stanza, messageBody);
                            if (ts != null){
                                query.addElement("body").setText(messageBody);
                            }
                        }
                    }
                }

                if (ts != null){
                    query.addAttribute("ts", String.valueOf(ts));
                    query.addAttribute("sid", messageStanzaId);
                    query.addAttribute("ma", messageAction);

                    //send reply to all resources of the user
                    Log.debug("Return IQ result to " + fromJID + ": " + reply.toString());
                    SessionManager.getInstance().userBroadcast(packet.getFrom().getNode(), reply);

                    //send result iq to all recipients
                    IQ pushIQ = reply.createCopy();
                    pushMessageCorrectIQ(fromJID, rm.toJID, pushIQ);

                    return null;
                }
            }
        }

        reply.setError(Condition.forbidden);
        return reply;
    }

    public void pushMessageCorrectIQ(String fromJID, String toJid, IQ pushIQ){

        ArrayList<JID> toJIDs = new ArrayList<>();
        if (toJid != null){

            JID toJID = new JID(toJid);

            MUCRoom room = null;
            try {
                MultiUserChatManager manager = XMPPServer.getInstance().getMultiUserChatManager();
                MultiUserChatService service = manager.getMultiUserChatService(toJID);
                room = service.getChatRoom(toJID.getNode());
            }
            catch(Exception e){}

            //to room
            if (room != null){
                Collection<MUCRole> roomOccupants = room.getOccupants();
                for (MUCRole roomOccupant : roomOccupants) {
                    JID roomOccupantJID = roomOccupant.getUserAddress().asBareJID();
                    if (roomOccupantJID != null && !roomOccupantJID.toString().equals(fromJID)) {
                        toJIDs.add(roomOccupantJID);
                    }
                }
            }
            //to user
            else{
                toJIDs.add(toJID);
            }

            if (toJIDs.size() > 0) {

                for (JID pushJID : toJIDs) {
                    try {
                        pushIQ.setID(new IQ().getID());
                        Log.debug("Send message correct push to " + pushJID.toString() + ": " + pushIQ.toString());
                        SessionManager.getInstance().userBroadcast(pushJID.getNode(), pushIQ);
                    } catch (Exception e) {
                        Log.debug(e.toString());
                    }
                }
            }
        }
    }

    public IQHandlerInfo getInfo() {
        return this.info;
    }

    public Iterator<String> getFeatures() {
        return Collections.singleton(NAMESPACE).iterator();
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        this.messageCorrectPersistenceManager = OutreachPlugin.getInstance().getMessageCorrectPersistenceManager();
    }
}
