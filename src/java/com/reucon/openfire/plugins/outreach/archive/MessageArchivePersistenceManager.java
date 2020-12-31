package com.reucon.openfire.plugins.outreach.archive;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatManager;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.stanzaid.StanzaIDUtil;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.sql.*;
import java.util.Date;
import java.util.*;

/**
 * Manages database persistence.
 */
public class MessageArchivePersistenceManager {

    private static final Logger Log = LoggerFactory.getLogger( MessageArchivePersistenceManager.class );
    public static final int DEFAULT_MAX = 1000;
    public static final int DEFAULT_MAX_RETRIEVABLE = 0;

    public static final String MESSAGE_ID = "ofMessageArchive.messageID";

    public static final String MESSAGE_SENT_DATE = "ofMessageArchive.sentDate";

    public static final String MESSAGE_DELETE_DATE = "ofMessageArchive.deleteDate";

    public static final String MESSAGE_EDIT_DATE = "ofMessageArchive.editDate";

    public static final String MESSAGE_TO_JID = "ofMessageArchive.toJID";

    public static final String MESSAGE_FROM_JID = "ofMessageArchive.fromJID";

    public static final String SELECT_MESSAGES = "SELECT DISTINCT " + "ofMessageArchive.fromJID, "
            + "ofMessageArchive.toJID, " + "ofMessageArchive.sentDate, " + "ofMessageArchive.stanza, "
            + "ofMessageArchive.messageID, " + "ofMessageArchive.deleteDate, " + "ofMessageArchive.editDate, " + "ofMessageArchive.fromJID "
            + "FROM ofMessageArchive "
            + "WHERE (ofMessageArchive.stanza IS NOT NULL OR ofMessageArchive.body IS NOT NULL) ";

    public static final String SELECT_MESSAGE_ORACLE = "SELECT "
            + "ofMessageArchive.fromJID, "
            + "ofMessageArchive.toJID, "
            + "ofMessageArchive.sentDate, "
            + "ofMessageArchive.stanza, "
            + "ofMessageArchive.messageID "
            + "FROM ofMessageArchive WHERE 1 = 1";

    public static final String COUNT_MESSAGES = "SELECT COUNT(DISTINCT ofMessageArchive.messageID) "
            + "FROM ofMessageArchive "
            + "WHERE (ofMessageArchive.stanza IS NOT NULL OR ofMessageArchive.body IS NOT NULL) ";

    private void appendWhere(StringBuilder sb, String... fragments) {
        if (sb.length() != 0) {
            sb.append(" AND ");
        }

        for (String fragment : fragments) {
            sb.append(fragment);
        }
    }

    public Date getAuditedStartDate(Date startDate) {
        long maxRetrievable = JiveGlobals.getIntProperty("conversation.maxRetrievable", DEFAULT_MAX_RETRIEVABLE)
                * JiveConstants.DAY;
        Date result = startDate;
        if (maxRetrievable > 0) {
            Date now = new Date();
            Date maxRetrievableDate = new Date(now.getTime() - maxRetrievable);
            if (startDate == null) {
                result = maxRetrievableDate;
            } else if (startDate.before(maxRetrievableDate)) {
                result = maxRetrievableDate;
            }
        }
        return result;
    }

    public Collection<ArchivedMessage> findMessages(Date startDate, Date endDate, JID owner, JID with, String query, XmppResultSet xmppResultSet, boolean useStableID) {

        Log.debug( "Finding messages of owner '{}' with start date '{}', end date '{}' with '{}' and resultset '{}', useStableId '{}'.", owner, startDate, endDate, with, xmppResultSet, useStableID );
        if ( query != null ) {
            Log.warn( "Query provided in search request, but not supported by implementation. Query is ignored!" );
        }
        final boolean isOracleDB = isOracleDB();

        final StringBuilder querySB;
        final StringBuilder whereSB;
        final StringBuilder limitSB;

        final TreeMap<Long, ArchivedMessage> archivedMessages = new TreeMap<Long, ArchivedMessage>();

        querySB = new StringBuilder( isOracleDB ? SELECT_MESSAGE_ORACLE : SELECT_MESSAGES );
        whereSB = new StringBuilder();
        limitSB = new StringBuilder();

        // Ignore legacy messages
        appendWhere(whereSB, MESSAGE_ID, " IS NOT NULL ");

        startDate = getAuditedStartDate(startDate);
        if (startDate != null) {
            appendWhere(whereSB, "( ", MESSAGE_SENT_DATE, " >= ?  OR (", MESSAGE_DELETE_DATE, " IS NOT NULL AND ", MESSAGE_DELETE_DATE, " >= ?) OR (", MESSAGE_EDIT_DATE, " IS NOT NULL AND ", MESSAGE_EDIT_DATE, " >= ?)) ");
            //appendWhere(whereSB, MESSAGE_SENT_DATE, " >= ?");
        }
        if (endDate != null) {
            appendWhere(whereSB, MESSAGE_SENT_DATE, " <= ?");
        }

        String source = null;
        if (owner != null && with != null) {

            MUCRoom room = null;
            try {
                MultiUserChatManager manager = XMPPServer.getInstance().getMultiUserChatManager();
                MultiUserChatService service = manager.getMultiUserChatService(with);
                room = service.getChatRoom(with.getNode());
            }
            catch(Exception e){}

            //room messages
            if (room != null){
                MUCRole.Affiliation aff = room.getAffiliation(owner);
                Log.debug( "Room affiliation: " + aff);

                //have permission
                if (aff == MUCRole.Affiliation.owner || aff == MUCRole.Affiliation.member || !room.isMembersOnly()) {
                    source = "room";
                    appendWhere(whereSB, "( ", MESSAGE_TO_JID, " = ? ) ");
                }
                else{
                    appendWhere(whereSB, "('1' = '2')");
                }
            }
            //user messages
            else{
                source = "user";
                appendWhere(whereSB, "(( ", MESSAGE_TO_JID, " = ? AND ", MESSAGE_FROM_JID, " = ? ) OR ( ", MESSAGE_FROM_JID, " = ? AND ", MESSAGE_TO_JID, " = ? )) ");
            }
        }
        else{
            appendWhere(whereSB, "('1' = '2')");
        }
        Log.debug( "Source: " + source);

        if (whereSB.length() != 0) {
            querySB.append(" AND ").append(whereSB);
        }

        if (DbConnectionManager.getDatabaseType() == DbConnectionManager.DatabaseType.sqlserver) {
            querySB.insert(0,"SELECT * FROM (SELECT *, ROW_NUMBER() OVER (ORDER BY "+MESSAGE_SENT_DATE+") AS RowNum FROM ( ");
            querySB.append(") ofMessageArchive ) t2 WHERE RowNum");
        }
        else {
            querySB.append(" ORDER BY ").append(MESSAGE_SENT_DATE);
        }

        if (xmppResultSet != null) {
            Integer firstIndex = null;
            int max = xmppResultSet.getMax() != null ? xmppResultSet.getMax() : DEFAULT_MAX;
            int count = countMessages(startDate, endDate, owner, with, whereSB.toString(), source);
            boolean reverse = false;

            xmppResultSet.setCount(count);
            if (xmppResultSet.getIndex() != null) {
                firstIndex = xmppResultSet.getIndex();
            } else if (xmppResultSet.getAfter() != null) {
                final Long needle;
                needle = Long.parseLong( xmppResultSet.getAfter() );
                firstIndex = countMessagesBefore(startDate, endDate, owner, with, needle, whereSB.toString(), source);
                firstIndex += 1;
            } else if (xmppResultSet.getBefore() != null) {
                final Long needle;
                needle = Long.parseLong( xmppResultSet.getBefore() );

                int messagesBeforeCount = countMessagesBefore(startDate, endDate, owner, with, needle, whereSB.toString(), source);
                firstIndex = messagesBeforeCount;
                firstIndex -= max;

                // Reduce result limit to number of results before (if less than a page remaining)
                if(messagesBeforeCount < max) {
                    max = messagesBeforeCount;
                }

                reverse = true;
                if (firstIndex < 0) {
                    firstIndex = 0;
                }
            }
            else if (xmppResultSet.isPagingBackwards()){
                int messagesCount = countMessages(startDate, endDate, owner, with, whereSB.toString(), source);
                firstIndex = messagesCount;
                firstIndex -= max;

                if (max > messagesCount) max = messagesCount;
                if (firstIndex < 0) firstIndex = 0;

                reverse = true;
            }
            firstIndex = firstIndex != null ? firstIndex : 0;

            if (DbConnectionManager.getDatabaseType() == DbConnectionManager.DatabaseType.sqlserver) {
                limitSB.append(" BETWEEN ").append(firstIndex+1);
                limitSB.append(" AND ").append(firstIndex+max);
            }
            else if( isOracleDB() ) {
                try {
                    final Statement statement = DbConnectionManager.getConnection().createStatement();
                    final ResultSet resultSet = statement.executeQuery( "select VERSION from PRODUCT_COMPONENT_VERSION P where P.PRODUCT like 'Oracle Database%'" );
                    resultSet.next();
                    final String versionString = resultSet.getString( "VERSION" );
                    final String[] versionParts = versionString.split( "\\." );
                    final int majorVersion = Integer.parseInt( versionParts[ 0 ] );
                    final int minorVersion = Integer.parseInt( versionParts[ 1 ] );

                    if( ( majorVersion == 12 && minorVersion >= 1 ) || majorVersion > 12 ) {
                        limitSB.append(" LIMIT ").append(max);
                        limitSB.append(" OFFSET ").append(firstIndex);
                    }
                    else {
                        querySB.insert( 0, "SELECT * FROM ( " );
                        limitSB.append( " ) WHERE rownum BETWEEN " )
                        .append( firstIndex + 1 )
                        .append( " AND " )
                        .append( firstIndex + max );
                    }
                } catch( SQLException e ) {
                    Log.warn( "Unable to determine oracle database version using fallback", e );
                    querySB.insert( 0, "SELECT * FROM ( " );
                    limitSB.append( " ) WHERE rownum BETWEEN " )
                    .append( firstIndex + 1 )
                    .append( " AND " )
                    .append( firstIndex + max );
                }
            }
            else {
                limitSB.append(" LIMIT ").append(max);
                limitSB.append(" OFFSET ").append(firstIndex);
            }
            xmppResultSet.setFirstIndex(firstIndex);

            if(isLastPage(firstIndex, count, max, reverse)) {
                xmppResultSet.setComplete(true);
            }
        }

        querySB.append(limitSB);

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(querySB.toString());
            bindMessageParameters(startDate, endDate, owner, with, pstmt, source);

            rs = pstmt.executeQuery();
            Log.debug("findMessages: SELECT_MESSAGES: " + pstmt.toString());
            while(rs.next()) {
                // TODO Can we replace this with #extractMessage?
                final String stanza = rs.getString( "stanza" );
                final long id = rs.getLong( "messageID" );
                final Date time = millisToDate(rs.getLong("sentDate"));

                UUID sid = null;
                if ( stanza != null && !stanza.isEmpty() ) {
                    try {
                        final Document doc = DocumentHelper.parseText( stanza );
                        final Message message = new Message( doc.getRootElement() );
                        sid = StanzaIDUtil.parseUniqueAndStableStanzaID( message, owner.toBareJID() );
                    } catch ( Exception e ) {
                        Log.warn( "An exception occurred while parsing message with ID {}", id, e );
                        sid = null;
                    }
                }

                ArchivedMessage archivedMessage = new ArchivedMessage(time, null, null, null, sid);
                archivedMessage.setId(id);
                archivedMessage.setStanza(stanza);
                if (rs.getLong("deleteDate") > 0) {
                    archivedMessage.setDeleteTime(rs.getLong("deleteDate"));
                }
                if (rs.getLong("editDate") > 0) {
                    archivedMessage.setEditTime(rs.getLong("editDate"));
                }
                archivedMessages.put(archivedMessage.getId(), archivedMessage);
            }
        } catch(SQLException sqle) {
            Log.error("Error selecting conversations", sqle);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        if (xmppResultSet != null && archivedMessages.size() > 0) {
            xmppResultSet.setFirst(String.valueOf( archivedMessages.firstKey() ));
            xmppResultSet.setLast(String.valueOf( archivedMessages.lastKey() ));
        }

        return archivedMessages.values();
    }

    private boolean isOracleDB()
    {
        return DbConnectionManager.getDatabaseType() == DbConnectionManager.DatabaseType.oracle;
    }

    private Integer countMessages(Date startDate, Date endDate,
            JID owner, JID with, String whereClause, String source) {

        StringBuilder querySB;

        querySB = new StringBuilder(COUNT_MESSAGES);
        if (whereClause != null && whereClause.length() != 0) {
            querySB.append(" AND ").append(whereClause);
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(querySB.toString());
            bindMessageParameters(startDate, endDate, owner, with, pstmt, source);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            } else {
                return 0;
            }
        } catch (SQLException sqle) {
            Log.error("Error counting conversations", sqle);
            return 0;
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    private Integer countMessagesBefore(Date startDate, Date endDate,
            JID owner, JID with, Long before, String whereClause, String source) {

        StringBuilder querySB;

        querySB = new StringBuilder(COUNT_MESSAGES);
        querySB.append(" AND ");
        if (whereClause != null && whereClause.length() != 0) {
            querySB.append(whereClause);
            querySB.append(" AND ");
        }
        querySB.append(MESSAGE_ID).append(" < ?");

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            int parameterIndex;
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(querySB.toString());
            parameterIndex = bindMessageParameters(startDate, endDate, owner, with, pstmt, source);
            pstmt.setLong(parameterIndex, before);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            } else {
                return 0;
            }
        } catch (SQLException sqle) {
            Log.error("Error counting conversations", sqle);
            return 0;
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    private int bindMessageParameters(Date startDate, Date endDate,
            JID owner, JID with, PreparedStatement pstmt, String source) throws SQLException {
        int parameterIndex = 1;

        if (startDate != null) {
            pstmt.setLong(parameterIndex++, dateToMillis(startDate));
        }
        if (endDate != null) {
            pstmt.setLong(parameterIndex++, dateToMillis(endDate));
        }
        if (source != null) {
            if (source.equals("room")) {
                pstmt.setString(parameterIndex++, with.toBareJID());
            }
            else if (source.equals("user")) {
                pstmt.setString(parameterIndex++, with.toBareJID());
                pstmt.setString(parameterIndex++, owner.toBareJID());
                pstmt.setString(parameterIndex++, with.toBareJID());
                pstmt.setString(parameterIndex++, owner.toBareJID());
            }
        }
        return parameterIndex;
    }

    private Long dateToMillis(Date date) {
        return date == null ? null : date.getTime();
    }

    private Date millisToDate(Long millis) {
        return millis == null ? null : new Date(millis);
    }

    /**
     * Determines whether a result page is the last of a set.
     *
     * @param firstItemIndex index (in whole set) of first item in page.
     * @param resultCount total number of results in set.
     * @param pageSize number of results in a page.
     * @param reverse whether paging is being performed in reverse (back to front)
     * @return whether results are from last page.
     */
    private boolean isLastPage(int firstItemIndex, int resultCount, int pageSize, boolean reverse) {

        if(reverse) {
            // Index of first item in last page always 0 when reverse
            if(firstItemIndex == 0) {
                return true;
            }
        } else {
            if((firstItemIndex + pageSize) >= resultCount) {
                return true;
            }
        }

        return false;
    }
}
