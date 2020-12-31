package com.reucon.openfire.plugins.outreach.archive;

import org.jivesoftware.openfire.forward.Forwarded;
import org.xmpp.packet.PacketExtension;

public final class Result extends PacketExtension {
    public Result(Forwarded forwarded, String xmlns, String queryId, String id, Long deleteDate, Long editDate) {
        super("result", xmlns);
        element.addAttribute("queryid", queryId);
        element.addAttribute("id", id);
        if (deleteDate != null){
            element.addAttribute("ddate", String.valueOf(deleteDate));
        }
        if (editDate != null){
            element.addAttribute("edate", String.valueOf(editDate));
        }
        element.add(forwarded.getElement());
    }
}
