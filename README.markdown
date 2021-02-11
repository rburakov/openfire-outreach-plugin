# Outreach Plugin Readme
Openfire plugin extends XEP-0313: Message Archive Management protocol to support Message Correction feature (deleting or editing a sent message) and provides retrieval for all one-to-one and MUC chat archive messages regardless of conversation limits.

## Usage
### Query message
In order to query archive for messages use [XEP-0313]( https://xmpp.org/extensions/xep-0313.html ) specification entities with qualified name 'urn:xmpp:mam:<b>2a</b>' instead of 'urn:xmpp:mam:2'.

Use `active` form field to skip deleted messages.

    <query xmlns="urn:xmpp:mam:2a"><x type="submit" xmlns="jabber:x:data"><field type="hidden" var="FORM_TYPE"><value>urn:xmpp:mam:2a</value></field><field var="active"/><field var="with"><value>romeo@montague.net</value></field></field></x><set xmlns="http://jabber.org/protocol/rsm"/></query>
Use `correct` form field to apply editDate/deleteDate for `start` filter.
    
    <query xmlns="urn:xmpp:mam:2a"><x type="submit" xmlns="jabber:x:data"><field type="hidden" var="FORM_TYPE"><value>urn:xmpp:mam:2a</value></field><field var="correct"/><field var="with"><value>romeo@montague.net</value></field><field var="start"><value>2021-02-08T10:40:06.000Z</value></field></x><set xmlns="http://jabber.org/protocol/rsm"/></query>

### Edit message
In order to edit your own message, the requesting entity sends an `<iq/>` stanza of type `set` to the target entity, containing an `<query/>` element qualified by the `jabber:iq:msg-correct` namespace.
Attribute `sid` contains message stanza id, attribute `ma` specifies edit action. Body element contains edited text.

    <iq id="iq1" type="set" xmlns="jabber:client"><query xmlns="jabber:iq:msg-correct" sid="message1" ma="edit"/><body>edited text</body></iq>

The target entity returns either an IQ-result or an IQ-error. When returning an IQ-result, the target entity sends an <iq/> stanza containing a `<query/>` element with a `ts` attribute.

    <iq id="iq1" to="romeo@montague.net" xmlns="jabber:client"><query xmlns="jabber:iq:msg-correct" sid="message1" ma="edit" ts="1589364007122"/><body>edited text</body></iq>

All message recipients get similar `<iq/>` stanza:

    <iq id="iq1-1" to="juliet@capulet.com" xmlns="jabber:client"><query xmlns="jabber:iq:msg-correct" sid="message1" ma="edit" ts="1589364007122"/><body>edited text</body></iq>

### Delete message
In order to delete your own message, the requesting entity sends an `<iq/>` stanza of type `set` to the target entity, containing an `<query/>` element qualified by the `jabber:iq:msg-correct` namespace. Attribute `sid` contains message stanza id, attribute `ma` specifies delete action.

    <iq id="iq2" type="set" xmlns="jabber:client"><query xmlns="jabber:iq:msg-correct" sid="message2" ma="delete"/></iq>

The target entity returns either an IQ-result or an IQ-error. When returning an IQ-result, the target entity sends an `<iq/>` stanza containing a `<query/>` element with a `ts` attribute.

    <iq id="iq2" to="romeo@montague.net" xmlns="jabber:client"><query xmlns="jabber:iq:msg-correct" sid="message2" ma="delete" ts="1589364007122"/></iq>

All message recipients get similar `<iq/>` stanza:

    <iq id="iq2-1" to="juliet@capulet.com" xmlns="jabber:client"><query xmlns="jabber:iq:msg-correct" sid="message2" ma="delete" ts="1589364007122"/></iq>

## Installation
`MonitoringPlugin` should be installed first.
Copy `outreach.jar` into the plugins directory of your Openfire installation. The plugin will then be automatically deployed.

## Database Schema</h2>
The `ofMessageArchive` table contains `deleteDate` and `editDate` timestamp fields.
