package com.hostlocal.dlna

/** Static UPnP device / service description documents. */
object DlnaDescriptions {
    const val CD_TYPE = "urn:schemas-upnp-org:service:ContentDirectory:1"
    const val CM_TYPE = "urn:schemas-upnp-org:service:ConnectionManager:1"
    const val DEVICE_TYPE = "urn:schemas-upnp-org:device:MediaServer:1"

    fun device(uuid: String, name: String): String = """<?xml version="1.0"?>
<root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:dlna="urn:schemas-dlna-org:device-1-0">
<specVersion><major>1</major><minor>0</minor></specVersion>
<device>
<dlna:X_DLNADOC>DMS-1.50</dlna:X_DLNADOC>
<deviceType>$DEVICE_TYPE</deviceType>
<friendlyName>${DlnaContent.escapeXml(name)}</friendlyName>
<manufacturer>HostLocal</manufacturer>
<modelName>HostLocal</modelName>
<modelNumber>1</modelNumber>
<UDN>uuid:$uuid</UDN>
<serviceList>
<service><serviceType>$CD_TYPE</serviceType><serviceId>urn:upnp-org:serviceId:ContentDirectory</serviceId><SCPDURL>/cd/scpd.xml</SCPDURL><controlURL>/cd/control</controlURL><eventSubURL>/cd/event</eventSubURL></service>
<service><serviceType>$CM_TYPE</serviceType><serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId><SCPDURL>/cm/scpd.xml</SCPDURL><controlURL>/cm/control</controlURL><eventSubURL>/cm/event</eventSubURL></service>
</serviceList>
</device>
</root>"""

    private class Arg(val name: String, val dir: String, val state: String)

    private fun scpd(actions: Map<String, List<Arg>>, vars: List<Triple<String, String, Boolean>>): String {
        val a = actions.entries.joinToString("") { (name, args) ->
            "<action><name>$name</name><argumentList>" +
                args.joinToString("") {
                    "<argument><name>${it.name}</name><direction>${it.dir}</direction>" +
                        "<relatedStateVariable>${it.state}</relatedStateVariable></argument>"
                } + "</argumentList></action>"
        }
        val v = vars.joinToString("") { (n, t, ev) ->
            "<stateVariable sendEvents=\"${if (ev) "yes" else "no"}\"><name>$n</name><dataType>$t</dataType></stateVariable>"
        }
        return """<?xml version="1.0"?>
<scpd xmlns="urn:schemas-upnp-org:service-1-0"><specVersion><major>1</major><minor>0</minor></specVersion>
<actionList>$a</actionList><serviceStateTable>$v</serviceStateTable></scpd>"""
    }

    val contentDirectoryScpd: String = scpd(
        mapOf(
            "Browse" to listOf(
                Arg("ObjectID", "in", "A_ARG_TYPE_ObjectID"), Arg("BrowseFlag", "in", "A_ARG_TYPE_BrowseFlag"),
                Arg("Filter", "in", "A_ARG_TYPE_Filter"), Arg("StartingIndex", "in", "A_ARG_TYPE_Index"),
                Arg("RequestedCount", "in", "A_ARG_TYPE_Count"), Arg("SortCriteria", "in", "A_ARG_TYPE_SortCriteria"),
                Arg("Result", "out", "A_ARG_TYPE_Result"), Arg("NumberReturned", "out", "A_ARG_TYPE_Count"),
                Arg("TotalMatches", "out", "A_ARG_TYPE_Count"), Arg("UpdateID", "out", "A_ARG_TYPE_UpdateID"),
            ),
            "GetSearchCapabilities" to listOf(Arg("SearchCaps", "out", "SearchCapabilities")),
            "GetSortCapabilities" to listOf(Arg("SortCaps", "out", "SortCapabilities")),
            "GetSystemUpdateID" to listOf(Arg("Id", "out", "SystemUpdateID")),
        ),
        listOf(
            Triple("A_ARG_TYPE_ObjectID", "string", false), Triple("A_ARG_TYPE_Result", "string", false),
            Triple("A_ARG_TYPE_BrowseFlag", "string", false), Triple("A_ARG_TYPE_Filter", "string", false),
            Triple("A_ARG_TYPE_SortCriteria", "string", false), Triple("A_ARG_TYPE_Index", "ui4", false),
            Triple("A_ARG_TYPE_Count", "ui4", false), Triple("A_ARG_TYPE_UpdateID", "ui4", false),
            Triple("SearchCapabilities", "string", false), Triple("SortCapabilities", "string", false),
            Triple("SystemUpdateID", "ui4", true),
        ),
    )

    val connectionManagerScpd: String = scpd(
        mapOf(
            "GetProtocolInfo" to listOf(Arg("Source", "out", "SourceProtocolInfo"), Arg("Sink", "out", "SinkProtocolInfo")),
            "GetCurrentConnectionIDs" to listOf(Arg("ConnectionIDs", "out", "CurrentConnectionIDs")),
            "GetCurrentConnectionInfo" to listOf(
                Arg("ConnectionID", "in", "A_ARG_TYPE_ConnectionID"), Arg("RcsID", "out", "A_ARG_TYPE_RcsID"),
                Arg("AVTransportID", "out", "A_ARG_TYPE_AVTransportID"), Arg("ProtocolInfo", "out", "A_ARG_TYPE_ProtocolInfo"),
                Arg("PeerConnectionManager", "out", "A_ARG_TYPE_ConnectionManager"),
                Arg("PeerConnectionID", "out", "A_ARG_TYPE_ConnectionID"),
                Arg("Direction", "out", "A_ARG_TYPE_Direction"), Arg("Status", "out", "A_ARG_TYPE_ConnectionStatus"),
            ),
        ),
        listOf(
            Triple("SourceProtocolInfo", "string", true), Triple("SinkProtocolInfo", "string", true),
            Triple("CurrentConnectionIDs", "string", true), Triple("A_ARG_TYPE_ConnectionStatus", "string", false),
            Triple("A_ARG_TYPE_ConnectionManager", "string", false), Triple("A_ARG_TYPE_Direction", "string", false),
            Triple("A_ARG_TYPE_ProtocolInfo", "string", false), Triple("A_ARG_TYPE_ConnectionID", "i4", false),
            Triple("A_ARG_TYPE_AVTransportID", "i4", false), Triple("A_ARG_TYPE_RcsID", "i4", false),
        ),
    )
}
