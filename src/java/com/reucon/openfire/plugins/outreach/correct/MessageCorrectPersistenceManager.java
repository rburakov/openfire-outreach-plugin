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

import static java.lang.Math.toIntExact;

/**
 * Default implementation of the PersistenceManager interface.
 */
public class MessageCorrectPersistenceManager
{
    private static final Logger Log = LoggerFactory.getLogger(OutreachPlugin.class);

    private static final String GET_MESSAGE =
            "SELECT * FROM ofMessageArchive WHERE messageId = ?";

    private static final String EDIT_MESSAGE =
            "UPDATE ofMessageArchive SET editDate = ?, stanza = REPLACE(stanza, body, ?), body = ? " +
                    "WHERE fromJID = ? AND messageId = ?";

    private static final String DELETE_MESSAGE =
        "UPDATE ofMessageArchive SET deleteDate = ? " +
            "WHERE fromJID = ? AND messageId = ?";

    public Integer editMessage(String fromJID, String messageId, String body){

        Connection con = null;
        PreparedStatement pstmt = null;
        int rowsUpdated = 0;
        int ts = toIntExact(new Date().getTime() / 1000);
        Integer returnTs = null;

        try
        {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(EDIT_MESSAGE);
            pstmt.setInt(1, ts);
            pstmt.setString(2, body);
            pstmt.setString(3, body);
            pstmt.setString(4, fromJID);
            pstmt.setString(5, messageId);
            rowsUpdated = pstmt.executeUpdate();
        }
        catch (SQLException e)
        {
            Log.error("Unable to edit ofMessageArchive for " + fromJID + " - " + messageId, e);
        }
        finally
        {
            DbConnectionManager.closeConnection(pstmt, con);
        }

        // if update did not affect any rows insert a new row
        if (rowsUpdated == 1)
        {
            returnTs = ts;
        }

        return returnTs;
    }

    public Integer deleteMessage(String fromJID, String messageId){

        Connection con = null;
        PreparedStatement pstmt = null;
        int rowsUpdated = 0;
        int ts = toIntExact(new Date().getTime() / 1000);
        Integer returnTs = null;

        try
        {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_MESSAGE);
            pstmt.setInt(1, ts);
            pstmt.setString(2, fromJID);
            pstmt.setString(3, messageId);
            rowsUpdated = pstmt.executeUpdate();
        }
        catch (SQLException e)
        {
            Log.error("Unable to delete ofMessageArchive for " + fromJID + " - " + messageId, e);
        }
        finally
        {
            DbConnectionManager.closeConnection(pstmt, con);
        }

        // if update did not affect any rows insert a new row
        if (rowsUpdated == 1)
        {
            returnTs = ts;
        }

        return returnTs;
    }

    public String getMessageToJID(String messageId){

        Connection con = null;
        PreparedStatement pstmt = null;
        String toJid = null;

        try
        {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_MESSAGE);
            pstmt.setString(1, messageId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                toJid = rs.getString("toJID");
            }
        }
        catch (SQLException e)
        {
            Log.error("Unable to get toJID from ofMessageArchive for " + messageId, e);
        }
        finally
        {
            DbConnectionManager.closeConnection(pstmt, con);
        }

        return toJid;
    }
}
