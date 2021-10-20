package com.reucon.openfire.plugins.outreach.correct;

import com.reucon.openfire.plugins.outreach.OutreachPlugin;
import org.jivesoftware.database.DbConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.regex.Matcher;

/**
 * Default implementation of the PersistenceManager interface.
 */
public class MessageCorrectPersistenceManager
{
    private static final Logger Log = LoggerFactory.getLogger(OutreachPlugin.class);

    private static final String GET_MESSAGE =
            "SELECT * FROM ofMessageArchive WHERE fromJID = ? AND stanza LIKE ? LIMIT 1";

    private static final String EDIT_MESSAGE =
            "UPDATE ofMessageArchive SET editDate = ?, stanza = ?, body = ? WHERE messageID = ?";

    private static final String DELETE_MESSAGE =
            "UPDATE ofMessageArchive SET deleteDate = ? WHERE messageID = ? ";

    public Long editMessage(long id, String stanza, String body){

        Connection con = null;
        PreparedStatement pstmt = null;
        int rowsUpdated = 0;
        long ts = new Date().getTime();
        Long returnTs = null;

        try
        {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(EDIT_MESSAGE);
            pstmt.setLong(1, ts);
            pstmt.setString(2, stanza);
            pstmt.setString(3, body);
            pstmt.setString(4, Long.toString(id));
            rowsUpdated = pstmt.executeUpdate();
        }
        catch (SQLException e)
        {
            Log.error("Unable to edit ofMessageArchive for " + id, e);
        }
        finally
        {
            DbConnectionManager.closeConnection(pstmt, con);
        }

        // set timestamp only when one row was affected
        if (rowsUpdated == 1)
        {
            returnTs = ts;
        }

        return returnTs;
    }

    public Long deleteMessage(long id){

        Connection con = null;
        PreparedStatement pstmt = null;
        int rowsUpdated = 0;
        long ts = new Date().getTime();
        Long returnTs = null;

        try
        {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_MESSAGE);
            pstmt.setLong(1, ts);
            pstmt.setString(2, Long.toString(id));
            rowsUpdated = pstmt.executeUpdate();
        }
        catch (SQLException e)
        {
            Log.error("Unable to delete ofMessageArchive for " + id, e);
        }
        finally
        {
            DbConnectionManager.closeConnection(pstmt, con);
        }

        // set timestamp only when one row was affected
        if (rowsUpdated == 1)
        {
            returnTs = ts;
        }

        return returnTs;
    }

    public GetMessageResult getMessage(String fromJID, String messageStanzaId){

        Connection con = null;
        PreparedStatement pstmt = null;
        GetMessageResult rm = null;

        try
        {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_MESSAGE);
            pstmt.setString(1, fromJID);
            pstmt.setString(2, "%id=\"" + messageStanzaId + "\"%");
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                long id = rs.getLong( "messageID" );
                String toJID = rs.getString( "toJID" );
                String stanza = rs.getString( "stanza" );

                if (toJID != null) {
                    rm = new GetMessageResult(id, toJID, stanza);
                }
            }
        }
        catch (SQLException e)
        {
            Log.error("Unable to get message from ofMessageArchive for " + messageStanzaId, e);
        }
        finally
        {
            DbConnectionManager.closeConnection(pstmt, con);
        }

        return rm;
    }

    public static class GetMessageResult {

        public long id;
        public String toJID;
        public String stanza;

        public GetMessageResult(long id, String toJID, String stanza){
            this.id = id;
            this.toJID = toJID;
            this.stanza = stanza;
        }
    }
}
