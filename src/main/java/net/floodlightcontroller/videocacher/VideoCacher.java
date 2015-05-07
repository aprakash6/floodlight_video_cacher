package net.floodlightcontroller.videocacher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.Wildcards.Flag;
import org.openflow.protocol.action.*;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;


public class VideoCacher implements IFloodlightModule, IOFMessageListener, IOFSwitchListener  {

	
	class TableEntry
	{
		public String ip;
		public short port;
		
		public TableEntry()
		{
			this.ip = "";
			this.port = 0;
		}
	}
	
	protected Map<String, byte[]> macTable;
	
	protected Map<Integer, OFFlowMod> ruleTable;
	protected Integer flowCount;
	
	protected IFloodlightProviderService floodlightProvider;
	protected static Logger logger;
	
	protected static short FLOWMOD_DEFAULT_IDLE_TIMEOUT = 32000; // in seconds
    protected static short FLOWMOD_DEFAULT_HARD_TIMEOUT = 0; // infinite
    
	private final String rootSw = "";
	private final String childSw = "";
	
	private final static String ROOT_IP = "10.10.1.2";
	private final static String ROOT_MAC = "42:5a:eb:06:7a:47";
	
	private final static String CHILD_UP_IP = "10.10.1.1";
	private final static String CHILD_UP_MAC = "";
	private final static String CHILD_DOWN_IP = "10.10.2.1";
	private final static String CHILD_DOWN_MAC = "";
	
	protected IStaticFlowEntryPusherService staticFlowEntryPusher;
	
	protected Integer totalSwitchesConnected = 0;
	
	
	protected long ovsMain = 39321; //00:00:00:00:00:00:99:99 (hex to decimal)
	
	protected long ovs11a = 4353; //00:00:00:00:00:00:11:01
	protected long ovs11b = 4354; //00:00:00:00:00:00:11:02
	
	protected long ovs21a = 8449; //00:00:00:00:00:00:21:01
	protected long ovs21b = 8450; //00:00:00:00:00:00:21:02
	protected long ovs22a = 8705; //00:00:00:00:00:00:22:01
	protected long ovs22b = 8706; //00:00:00:00:00:00:22:02
	
	protected long ovs31a = 12545; //00:00:00:00:00:00:31:01
	protected long ovs31b = 12546; //00:00:00:00:00:00:31:02
	protected long ovs32a = 12801; //00:00:00:00:00:00:32:01
	protected long ovs32b = 12802; //00:00:00:00:00:00:32:02
	protected long ovs33a = 13057; //00:00:00:00:00:00:33:01
	protected long ovs33b = 13058; //00:00:00:00:00:00:33:02
	protected long ovs34a = 13313; //00:00:00:00:00:00:34:01
	protected long ovs34b = 13314; //00:00:00:00:00:00:34:02
	
	@Override
	public String getName() {
		return VideoCacher.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
	    Collection<Class<? extends IFloodlightService>> l =
	        new ArrayList<Class<? extends IFloodlightService>>();
	    l.add(IFloodlightProviderService.class);
	    l.add(IStaticFlowEntryPusherService.class);
	    
	    return l;
	}
	
	

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		staticFlowEntryPusher = context.getServiceImpl(IStaticFlowEntryPusherService.class);
	    logger = LoggerFactory.getLogger(VideoCacher.class);
	    macTable = new HashMap <String, byte[]>();
	    ruleTable = new HashMap <Integer, OFFlowMod>();
	    flowCount = 0;
		
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		floodlightProvider.addOFSwitchListener(this); 
		
	}
	
	/*
	 * push a packet-out to the switch
	 * */
	private void pushPacket(IOFSwitch sw, OFMatch match, OFPacketIn pi, short outport) {
		
		// create an OFPacketOut for the pushed packet
        OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory()
                		.getMessage(OFType.PACKET_OUT);        
        
        // update the inputPort and bufferID
        po.setInPort(pi.getInPort());
        po.setBufferId(pi.getBufferId());
                
        // define the actions to apply for this packet
        OFActionOutput action = new OFActionOutput();
		action.setPort(outport);		
		po.setActions(Collections.singletonList((OFAction)action));
		po.setActionsLength((short)OFActionOutput.MINIMUM_LENGTH);
	        
        // set data if it is included in the packet in but buffer id is NONE
        if (pi.getBufferId() == OFPacketOut.BUFFER_ID_NONE) {
            byte[] packetData = pi.getPacketData();
            po.setLength(U16.t(OFPacketOut.MINIMUM_LENGTH
                    + po.getActionsLength() + packetData.length));
            po.setPacketData(packetData);
        } else {
            po.setLength(U16.t(OFPacketOut.MINIMUM_LENGTH
                    + po.getActionsLength()));
        }        
        
        // push the packet to the switch
        try {
            sw.write(po, null);
        } catch (IOException e) {
            logger.error("failed to write packetOut: ", e);
        }
	}
	
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		OFMatch match = new OFMatch();
        match.loadFromPacket(((OFPacketIn)msg).getPacketData(), 
        					 ((OFPacketIn)msg).getInPort());
		
		if (match.getDataLayerType() != Ethernet.TYPE_IPv4)
			return Command.CONTINUE;
		
		switch (msg.getType()) {
		
		case PACKET_IN:
			//logger.debug(msg.toString());
			//logger.warn("Receive a packet !");
			
			if ( match.getTransportDestination() == 30000 )
				return this.newRequestFromClient(sw, (OFPacketIn)msg );
			
			if ( match.getNetworkSource() == IPv4.toIPv4Address(ROOT_IP) )
				return this.addFlowToDuplicateStream( sw, (OFPacketIn)msg );
			
		default:
			break;
		}
		
		return Command.CONTINUE;
	}
	
	private Command newRequestFromClient(IOFSwitch sw, OFPacketIn pi) 
	{
		
		flowCount++;
		
		// Read in packet data headers by using an OFMatch structure
        OFMatch match = new OFMatch();
        match.loadFromPacket(pi.getPacketData(), pi.getInPort());		
        
		// take the source and destination mac from the packet
		Long sourceMac = Ethernet.toLong(match.getDataLayerSource());
        Long destMac   = Ethernet.toLong(match.getDataLayerDestination());
        
        String srcIp = IPv4.fromIPv4Address(match.getNetworkSource());
        String destIp = IPv4.fromIPv4Address(match.getNetworkDestination());
        
        short srcPort = match.getTransportSource();
        short destPort = match.getTransportDestination();
         
        Short inPort = pi.getInPort();
        
        TableEntry ipPortEntry = new TableEntry();
        ipPortEntry.ip = srcIp;
        ipPortEntry.port = srcPort;
        
        String entryVal = ipPortEntry.ip + ipPortEntry.port;
        
        if(!macTable.containsKey(ipPortEntry))
        {
        	macTable.put(entryVal, match.getDataLayerSource());
        }
       
        
		
			
			 Short outPort = OFPort.OFPP_LOCAL.getValue();
			 
			//add flow rule to pass on these kind of requests to the higher level of switch
			// create the rule and specify it's an ADD rule
        	OFMatch match2 = new OFMatch();
        	OFFlowMod rule = new OFFlowMod();
 			rule.setType(OFType.FLOW_MOD); 			
 			rule.setCommand(OFFlowMod.OFPFC_ADD);
 			
 			int wildcards1 = ( (Integer) sw 
						.getAttribute(IOFSwitch.PROP_FASTWILDCARDS))
						.intValue()
						& ~OFMatch.OFPFW_NW_PROTO
						& ~OFMatch.OFPFW_IN_PORT
						& ~OFMatch.OFPFW_DL_TYPE
						& ~OFMatch.OFPFW_DL_VLAN
						& ~OFMatch.OFPFW_DL_SRC
						& ~OFMatch.OFPFW_DL_DST
						& ~OFMatch.OFPFW_NW_SRC_MASK
						& ~OFMatch.OFPFW_NW_DST_MASK;
 			
 			match2.setWildcards(wildcards1); 
 			
 			match2.setNetworkProtocol(match.getNetworkProtocol());
 			match2.setInputPort(inPort);
 			match2.setDataLayerType(match.getDataLayerType());
 			match2.setDataLayerVirtualLan(match.getDataLayerVirtualLan());
 			match2.setDataLayerSource(match.getDataLayerSource());
 			match2.setDataLayerDestination(match.getDataLayerDestination());
 			match2.setNetworkSource(match.getNetworkSource());
 			match2.setNetworkDestination(match.getNetworkDestination());
 			match2.setNetworkTypeOfService(match.getNetworkTypeOfService());
 			
 			rule.setMatch(match2);
 			
 			// specify timers for the life of the rule
 			rule.setIdleTimeout(VideoCacher.FLOWMOD_DEFAULT_IDLE_TIMEOUT);
 			rule.setHardTimeout(VideoCacher.FLOWMOD_DEFAULT_HARD_TIMEOUT);
 	        
 	        // set the buffer id to NONE - implementation artifact
 			rule.setBufferId(OFPacketOut.BUFFER_ID_NONE);
 	       
 	        // set of actions to apply to this rule
 			ArrayList<OFAction> actions = new ArrayList<OFAction>();
 			
 			//Setting actions to just send them onto the higher level switch (root switch)
 			
 			
 			//************SET OUTPUT PORT HERE*******************
 			OFAction out1 = new OFActionOutput(outPort);
 			
 			
 			OFActionDataLayerSource dlSrc = new OFActionDataLayerSource();
 			OFActionDataLayerDestination dlDst = new OFActionDataLayerDestination();
 			OFActionNetworkLayerSource nwSrc = new OFActionNetworkLayerSource();
 			OFActionNetworkLayerDestination nwDst = new OFActionNetworkLayerDestination();
 			OFActionTransportLayerSource tlSrc = new OFActionTransportLayerSource();
 			OFActionTransportLayerDestination tlDst = new OFActionTransportLayerDestination();
 			
 			//dlSrc.setDataLayerAddress(Ethernet.toMACAddress(CHILD_UP_MAC));
 			dlDst.setDataLayerAddress(Ethernet.toMACAddress(ROOT_MAC));
 			
 			//nwSrc.setNetworkAddress(IPv4.toIPv4Address(CHILD_UP_IP));
 			nwDst.setNetworkAddress(IPv4.toIPv4Address(ROOT_IP));
 			
 			//actions.add(dlSrc);
 			actions.add(dlDst);
 			//actions.add(nwSrc);
 			actions.add(nwDst);
 			//actions.add(tlSrc);
 			//actions.add(tlDst);
 			actions.add(out1);
 			
 			rule.setActions(actions);
 			
 			//logger.warn(actions.toString());
 			
 			// specify the length of the flow structure created
// 			rule.setLength((short) (OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH)); 			
 			
 			int actionsLength = ( OFActionOutput.MINIMUM_LENGTH + 
 								  //OFActionDataLayerSource.MINIMUM_LENGTH + 
 								  OFActionDataLayerDestination.MINIMUM_LENGTH + 
 								  //OFActionNetworkLayerSource.MINIMUM_LENGTH + 
 								  OFActionNetworkLayerDestination.MINIMUM_LENGTH); 
 								  //OFActionTransportLayerSource.MINIMUM_LENGTH + 
 								  //OFActionTransportLayerDestination.MINIMUM_LENGTH);
 								  
 								 
 			rule.setLengthU( (OFFlowMod.MINIMUM_LENGTH + actionsLength) ); 			
 			
 			//logger.debug("install rule for destination {}", destMac);
 			
 			
 			
 			try {
 				sw.write(rule, null);
 			} catch (Exception e) {
 				e.printStackTrace();
 			}	
 			
 			this.pushPacket(sw, match2, pi, outPort);
 			
		
		
	
		return Command.CONTINUE;
	}
	
	
	private Command addFlowToDuplicateStream(IOFSwitch sw, OFPacketIn pi)
	{
		// Read in packet data headers by using an OFMatch structure
        OFMatch match = new OFMatch();
        match.loadFromPacket(pi.getPacketData(), pi.getInPort());		
        
		// take the source and destination mac from the packet
		Long sourceMac = Ethernet.toLong(match.getDataLayerSource());
        Long destMac   = Ethernet.toLong(match.getDataLayerDestination());
        
        String srcIp = IPv4.fromIPv4Address(match.getNetworkSource());
        String destIp = IPv4.fromIPv4Address(match.getNetworkDestination());
        
        short srcPort = match.getTransportSource();
        short destPort = match.getTransportDestination();
         
        Short inPort = pi.getInPort();

        Short outPort2 = 1;
			 
		TableEntry ipPortEntry = new TableEntry();
		byte[] retrievedMac;
		
		ipPortEntry.ip = destIp;
		ipPortEntry.port = destPort;
		
		String toRetrieveMac = ipPortEntry.ip + ipPortEntry.port;
		
		logger.warn("ip = " + ipPortEntry.ip + ", port = " + ipPortEntry.port );
		if (!macTable.containsKey(toRetrieveMac))
			logger.warn("<<<<<<<<<Something is wrong>>>>>>>>>");
		
		retrievedMac = macTable.get(toRetrieveMac);
		
		//add flow rule in child switch to duplicate flows
			
		// create the rule and specify it's an ADD rule
        OFMatch match2 = new OFMatch();
        OFFlowMod rule = new OFFlowMod();
 		rule.setType(OFType.FLOW_MOD); 			
 		rule.setCommand(OFFlowMod.OFPFC_ADD);
 			
 		int wildcards = ( (Integer) sw 
					.getAttribute(IOFSwitch.PROP_FASTWILDCARDS))
					.intValue()
					& ~OFMatch.OFPFW_NW_PROTO
					& ~OFMatch.OFPFW_IN_PORT
					& ~OFMatch.OFPFW_DL_TYPE
					& ~OFMatch.OFPFW_DL_VLAN
					& ~OFMatch.OFPFW_DL_SRC
					& ~OFMatch.OFPFW_DL_DST
					& ~OFMatch.OFPFW_NW_SRC_MASK
					& ~OFMatch.OFPFW_NW_DST_MASK;
 			
 		match2.setWildcards(wildcards); 
 		
 		match2.setNetworkProtocol(match.getNetworkProtocol());
 		match2.setInputPort(inPort);
 		match2.setDataLayerType(match.getDataLayerType());
 		match2.setDataLayerVirtualLan(match.getDataLayerVirtualLan());
 		match2.setDataLayerSource(match.getDataLayerSource());
 		match2.setDataLayerDestination(match.getDataLayerDestination());
 		match2.setNetworkSource(match.getNetworkSource());
 		match2.setNetworkDestination(match.getNetworkDestination());
 		match2.setNetworkTypeOfService(match.getNetworkTypeOfService());
 		
 		rule.setMatch(match2);
 		
 		// specify timers for the life of the rule
 		rule.setIdleTimeout(VideoCacher.FLOWMOD_DEFAULT_IDLE_TIMEOUT);
 		rule.setHardTimeout(VideoCacher.FLOWMOD_DEFAULT_HARD_TIMEOUT);
 	       
 	    // set the buffer id to NONE - implementation artifact
 		rule.setBufferId(OFPacketOut.BUFFER_ID_NONE);
 	       
 	    // set of actions to apply to this rule
 		ArrayList<OFAction> actions = new ArrayList<OFAction>();
 			
 			
 			
 		//************SET OUTPUT PORT HERE*******************
 		OFAction out1 = new OFActionOutput(outPort2);
 			
 		OFActionDataLayerSource dlSrc = new OFActionDataLayerSource();
 		OFActionDataLayerDestination dlDst = new OFActionDataLayerDestination();
 		OFActionNetworkLayerSource nwSrc = new OFActionNetworkLayerSource();
 		OFActionNetworkLayerDestination nwDst = new OFActionNetworkLayerDestination();
 		OFActionTransportLayerSource tlSrc = new OFActionTransportLayerSource();
 		OFActionTransportLayerDestination tlDst = new OFActionTransportLayerDestination();
 		
 		//dlSrc.setDataLayerAddress(Ethernet.toMACAddress(CHILD_UP_MAC));
 		dlDst.setDataLayerAddress(retrievedMac);
 		
 		//nwSrc.setNetworkAddress(IPv4.toIPv4Address(CHILD_UP_IP));
 		//nwDst.setNetworkAddress(IPv4.toIPv4Address(ROOT_IP));
 		
 		//actions.add(dlSrc);
 		actions.add(dlDst);
 		//actions.add(nwSrc);
 		//actions.add(nwDst);
 		//actions.add(tlSrc);
 		//actions.add(tlDst);
 		actions.add(out1);
 		
 		rule.setActions(actions);
 		
 		//logger.warn(actions.toString());
 		
 		//specify the length of the flow structure created
 		//rule.setLength((short) (OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH)); 			
 		
 		int actionsLength = ( OFActionOutput.MINIMUM_LENGTH + 
 							  //OFActionDataLayerSource.MINIMUM_LENGTH + 
 							  OFActionDataLayerDestination.MINIMUM_LENGTH); 
 							  //OFActionNetworkLayerSource.MINIMUM_LENGTH + 
 							  //OFActionNetworkLayerDestination.MINIMUM_LENGTH); 
 							  //OFActionTransportLayerSource.MINIMUM_LENGTH + 
 							  //OFActionTransportLayerDestination.MINIMUM_LENGTH);
 							  
 							 
 		rule.setLengthU( (OFFlowMod.MINIMUM_LENGTH + actionsLength) ); 			
 		
 		//logger.debug("install rule for destination {}", destMac);
 		 
 		ruleTable.put(flowCount, rule);
 		
 		try {
 			sw.write(rule, null);
 		} catch (Exception e) {
 			e.printStackTrace();
 		}	
		
 		
				
 		return Command.CONTINUE;
	}
	

	@Override
	public void switchAdded(long switchId) 
	{
		OFMatch matchArp = new OFMatch();
		OFFlowMod ruleArp = new OFFlowMod();
		ruleArp.setType(OFType.FLOW_MOD);
		ruleArp.setCommand(OFFlowMod.OFPFC_ADD);
		ruleArp.setBufferId(OFPacketOut.BUFFER_ID_NONE);
		ruleArp.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT);
		ruleArp.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT);
		matchArp.setDataLayerType(Ethernet.TYPE_ARP);
		//set everything to wildcards except nw_proto
		matchArp.setWildcards(~OFMatch.OFPFW_DL_TYPE);
		ruleArp.setMatch(matchArp);
		ArrayList<OFAction> arpActions = new ArrayList<OFAction>();
		OFAction outArp = new OFActionOutput(OFPort.OFPP_FLOOD.getValue());
		arpActions.add(outArp);
		ruleArp.setActions(arpActions);
		ruleArp.setLengthU(OFFlowMod.MINIMUM_LENGTH
							+ OFActionOutput.MINIMUM_LENGTH );
		//staticFlowEntryPusher.addFlow("arp", ruleArp, floodlightProvider.getSwitch(switchId).getStringId() );
		
		
		try {
			floodlightProvider.getSwitch(switchId).write(ruleArp, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		OFMatch matchIcmp = new OFMatch();
		OFFlowMod ruleIcmp = new OFFlowMod();
		ruleIcmp.setType(OFType.FLOW_MOD);
		ruleIcmp.setCommand(OFFlowMod.OFPFC_ADD);
		ruleIcmp.setBufferId(OFPacketOut.BUFFER_ID_NONE);
		ruleIcmp.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT);
		ruleIcmp.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT);
		matchIcmp.setDataLayerType(Ethernet.TYPE_IPv4);
		matchIcmp.setNetworkProtocol(IPv4.PROTOCOL_ICMP);
		//set everything to wildcards except nw_proto and dl_type
		matchIcmp.setWildcards(~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_DL_TYPE);
		ruleIcmp.setMatch(matchIcmp);
		ArrayList<OFAction> icmpActions = new ArrayList<OFAction>();
		OFAction outIcmp = new OFActionOutput(OFPort.OFPP_FLOOD.getValue());
		icmpActions.add(outIcmp);
		ruleIcmp.setActions(icmpActions);
		ruleIcmp.setLengthU(OFFlowMod.MINIMUM_LENGTH
							+ OFActionOutput.MINIMUM_LENGTH );
		
		try {
			floodlightProvider.getSwitch(switchId).write(ruleIcmp, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		logger.debug("switch id = {}",switchId );
		
		totalSwitchesConnected++;
		
		if ( totalSwitchesConnected == 15)
			this.addInitialFlows();
		
	}
	
	private void addInitialFlows()
	{
		OFMatch matchReqLowerSw = new OFMatch();
		OFFlowMod ruleReqLowerSw = new OFFlowMod();
		ruleReqLowerSw.setType(OFType.FLOW_MOD);
		ruleReqLowerSw.setCommand(OFFlowMod.OFPFC_ADD);
		ruleReqLowerSw.setBufferId(OFPacketOut.BUFFER_ID_NONE);
		ruleReqLowerSw.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT);
		ruleReqLowerSw.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT);
		matchReqLowerSw.setDataLayerType(Ethernet.TYPE_IPv4);
		matchReqLowerSw.setNetworkProtocol(IPv4.PROTOCOL_UDP);
		matchReqLowerSw.setInputPort((short)1);
		//set everything to wildcards except nw_proto and dl_type
		matchReqLowerSw.setWildcards(~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_DL_TYPE);
		ruleReqLowerSw.setMatch(matchReqLowerSw);
		ArrayList<OFAction> reqLowerSwActions = new ArrayList<OFAction>();
		OFAction outReqLowerSw = new OFActionOutput((short)2);
		reqLowerSwActions.add(outReqLowerSw);
		ruleReqLowerSw.setActions(reqLowerSwActions);
		ruleReqLowerSw.setLengthU(OFFlowMod.MINIMUM_LENGTH
							+ OFActionOutput.MINIMUM_LENGTH );
		
		try {
			floodlightProvider.getSwitch(ovs31b).write(ruleReqLowerSw, null);
			floodlightProvider.getSwitch(ovs32b).write(ruleReqLowerSw, null);
			floodlightProvider.getSwitch(ovs33b).write(ruleReqLowerSw, null);
			floodlightProvider.getSwitch(ovs34b).write(ruleReqLowerSw, null);
			floodlightProvider.getSwitch(ovs21b).write(ruleReqLowerSw, null);
			floodlightProvider.getSwitch(ovs22b).write(ruleReqLowerSw, null);
			floodlightProvider.getSwitch(ovs11b).write(ruleReqLowerSw, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		OFMatch matchReqHigherSw = new OFMatch();
		OFFlowMod ruleReqHigherSw = new OFFlowMod();
		ruleReqHigherSw.setType(OFType.FLOW_MOD);
		ruleReqHigherSw.setCommand(OFFlowMod.OFPFC_ADD);
		ruleReqHigherSw.setBufferId(OFPacketOut.BUFFER_ID_NONE);
		ruleReqHigherSw.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT);
		ruleReqHigherSw.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT);
		matchReqHigherSw.setDataLayerType(Ethernet.TYPE_IPv4);
		matchReqHigherSw.setNetworkProtocol(IPv4.PROTOCOL_UDP);
		matchReqHigherSw.setInputPort((short)2);
		//set everything to wildcards except nw_proto and dl_type
		matchReqHigherSw.setWildcards(~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_DL_TYPE);
		ruleReqHigherSw.setMatch(matchReqHigherSw);
		ArrayList<OFAction> reqHigherSwActions = new ArrayList<OFAction>();
		OFAction outReqHigherSw = new OFActionOutput((short)1);
		reqHigherSwActions.add(outReqHigherSw);
		ruleReqHigherSw.setActions(reqHigherSwActions);
		ruleReqHigherSw.setLengthU(OFFlowMod.MINIMUM_LENGTH
							+ OFActionOutput.MINIMUM_LENGTH );
		
		try {
			floodlightProvider.getSwitch(ovs31a).write(ruleReqHigherSw, null);
			floodlightProvider.getSwitch(ovs32a).write(ruleReqHigherSw, null);
			floodlightProvider.getSwitch(ovs33a).write(ruleReqHigherSw, null);
			floodlightProvider.getSwitch(ovs34a).write(ruleReqHigherSw, null);
			floodlightProvider.getSwitch(ovs21a).write(ruleReqHigherSw, null);
			floodlightProvider.getSwitch(ovs22a).write(ruleReqHigherSw, null);
			floodlightProvider.getSwitch(ovs11a).write(ruleReqHigherSw, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void switchRemoved(long switchId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void switchActivated(long switchId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void switchPortChanged(long switchId, ImmutablePort port,
			PortChangeType type) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void switchChanged(long switchId) {
		// TODO Auto-generated method stub
		
	}
	
	
	

}
