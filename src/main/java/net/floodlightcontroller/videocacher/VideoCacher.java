package net.floodlightcontroller.videocacher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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
import net.floodlightcontroller.core.internal.FloodlightProvider;
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
		public byte[] mac;
		
		public TableEntry()
		{
			this.ip = "";
			this.port = 0;
			this.mac = "00:00:00:00:00:00".getBytes();
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
	
	private final static String ROOT_IP = "10.10.1.1";
	private final static String ROOT_MAC = "52:19:b4:43:7d:42";
	
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
        ipPortEntry.mac = Ethernet.toByteArray(sourceMac);
        
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
						& ~OFMatch.OFPFW_NW_DST_MASK
 						& ~OFMatch.OFPFW_TP_SRC
 						& ~OFMatch.OFPFW_TP_DST;
 			
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
 			match2.setTransportSource(match.getTransportSource());
 			match2.setTransportDestination(match.getTransportDestination());
 			
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
 			
 			
 			/*----------Add a duplication rule at a particular switch before allowing the movie request
 			 to go through---------------------------------------------------------------------------*/
 			
 			if (flowCount > 1)
 				this.addFlowToDuplicateStream(ipPortEntry);
 			
 			
 			try {
 				sw.write(rule, null);
 			} catch (Exception e) {
 				e.printStackTrace();
 			}	
 			
 			this.pushPacket(sw, match2, pi, outPort);
 			
		
		
	
		return Command.CONTINUE;
	}
	
	
	private void addFlowToDuplicateStream(TableEntry ipPortMacEntry)
	{
//		String swToAddDuplication = floodlightProvider.getSwitch(ovs21b).getStringId();
//		logger.debug("---------------------- " + swToAddDuplication + " ------------");
//		
//		Map<String, OFFlowMod> listOfFlows = new HashMap<String, OFFlowMod>(); 
//		
//		listOfFlows = staticFlowEntryPusher.getFlows(swToAddDuplication);
//		
//		logger.debug("---------------------- " + listOfFlows + " ------------");
//		
//		OFFlowMod curFlow = new OFFlowMod(); 
//		curFlow = listOfFlows.get("MovieLower");
//		
//		logger.debug("---------------------- " + curFlow + " ------------");
//		
//		ArrayList<OFAction> curActions = new ArrayList<OFAction>();
//		curActions = (ArrayList<OFAction>) curFlow.getActions();
//		Iterator itr = curActions.iterator();
//	      while(itr.hasNext()) {
//	         OFAction ele = (OFAction) itr.next();
//	         logger.debug("---------------------- " + ele + " ------------");
//	      }
	
		
		OFMatch newMatch = new OFMatch();
		OFFlowMod newRule = new OFFlowMod();
		newRule.setType(OFType.FLOW_MOD);
		newRule.setCommand(OFFlowMod.OFPFC_ADD);
		newRule.setBufferId(OFPacketOut.BUFFER_ID_NONE);
		newRule.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT);
		newRule.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT);
		newMatch.setDataLayerType(Ethernet.TYPE_IPv4);
		newMatch.setNetworkProtocol(IPv4.PROTOCOL_UDP);
		newMatch.setNetworkSource(IPv4.toIPv4Address(ROOT_IP));
		newMatch.setTransportSource((short) 33333);
		newMatch.setInputPort(OFPort.OFPP_LOCAL.getValue());
		//set everything to wildcards except nw_proto and dl_type
		newMatch.setWildcards(~OFMatch.OFPFW_NW_PROTO 
									& ~OFMatch.OFPFW_DL_TYPE
									& ~OFMatch.OFPFW_NW_DST_ALL
									& ~OFMatch.OFPFW_TP_DST);
		newRule.setMatch(newMatch);
		
		ArrayList<OFAction> newActions = new ArrayList<OFAction>();
		OFAction outOrig = new OFActionOutput((short) 1);
		newActions.add(outOrig);
		
		OFActionDataLayerDestination dlDst = new OFActionDataLayerDestination();
		OFActionNetworkLayerDestination nwDst = new OFActionNetworkLayerDestination();
		OFActionTransportLayerDestination tlDst = new OFActionTransportLayerDestination();
		
		dlDst.setDataLayerAddress(ipPortMacEntry.mac);
		nwDst.setNetworkAddress(IPv4.toIPv4Address(ipPortMacEntry.ip));
		tlDst.setTransportPort(ipPortMacEntry.port);
		
		OFAction outNew = new OFActionOutput((short) 1);
		newActions.add(outNew);
		
		newActions.add(dlDst);
		newActions.add(nwDst);
		newActions.add(outNew);
		
		newRule.setActions(newActions);
		
		int actionsLength = ( OFActionOutput.MINIMUM_LENGTH + 
							  //OFActionDataLayerSource.MINIMUM_LENGTH + 
							  OFActionDataLayerDestination.MINIMUM_LENGTH + 
							  //OFActionNetworkLayerSource.MINIMUM_LENGTH + 
							  OFActionNetworkLayerDestination.MINIMUM_LENGTH + 
							  //OFActionTransportLayerSource.MINIMUM_LENGTH + 
							  OFActionTransportLayerDestination.MINIMUM_LENGTH);
							  
							 
		newRule.setLengthU( (OFFlowMod.MINIMUM_LENGTH + actionsLength) ); 		
		
		try {
				floodlightProvider.getSwitch(ovs21b).write(newRule, null);
			} catch (Exception e) {
				e.printStackTrace();
			}	
		
		
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
	
	public void addInitialFlows()
	{
		logger.debug("<<<<<<<<<<<<<<<Entering addInitialFlows()!>>>>>>>>>>>>");
		
		//---------------------------------------for all movie requests (lower switch)-----------------------------

		logger.debug("<<<<<<<<<<<<<<<MOVIE REQUESTS ON LOWER SWITCHES>>>>>>>>>>>>");
		
		
		OFMatch matchReqLowerSw = new OFMatch();
		OFFlowMod ruleReqLowerSw = new OFFlowMod();
		ruleReqLowerSw.setType(OFType.FLOW_MOD);
		ruleReqLowerSw.setCommand(OFFlowMod.OFPFC_ADD);
		ruleReqLowerSw.setBufferId(OFPacketOut.BUFFER_ID_NONE);
		ruleReqLowerSw.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT);
		ruleReqLowerSw.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT);
		matchReqLowerSw.setDataLayerType(Ethernet.TYPE_IPv4);
		matchReqLowerSw.setNetworkProtocol(IPv4.PROTOCOL_UDP);
		matchReqLowerSw.setTransportDestination((short) 30000);
		matchReqLowerSw.setInputPort((short)1);
		//set everything to wildcards except nw_proto and dl_type
		matchReqLowerSw.setWildcards(~OFMatch.OFPFW_NW_PROTO & 
									 ~OFMatch.OFPFW_DL_TYPE & 
									 ~OFMatch.OFPFW_TP_DST);
		ruleReqLowerSw.setMatch(matchReqLowerSw);
		ArrayList<OFAction> reqLowerSwActions = new ArrayList<OFAction>();
		OFAction outReqLowerSw = new OFActionOutput(OFPort.OFPP_LOCAL.getValue());
		reqLowerSwActions.add(outReqLowerSw);
		ruleReqLowerSw.setActions(reqLowerSwActions);
		ruleReqLowerSw.setLengthU(OFFlowMod.MINIMUM_LENGTH
							+ OFActionOutput.MINIMUM_LENGTH );
		
		staticFlowEntryPusher.addFlow("reqLowerSw", ruleReqLowerSw, floodlightProvider.getSwitch(ovs11b).getStringId() );
		staticFlowEntryPusher.addFlow("reqLowerSw", ruleReqLowerSw, floodlightProvider.getSwitch(ovs21b).getStringId() );
		staticFlowEntryPusher.addFlow("reqLowerSw", ruleReqLowerSw, floodlightProvider.getSwitch(ovs22b).getStringId() );
		staticFlowEntryPusher.addFlow("reqLowerSw", ruleReqLowerSw, floodlightProvider.getSwitch(ovs31b).getStringId() );
		staticFlowEntryPusher.addFlow("reqLowerSw", ruleReqLowerSw, floodlightProvider.getSwitch(ovs32b).getStringId() );
		staticFlowEntryPusher.addFlow("reqLowerSw", ruleReqLowerSw, floodlightProvider.getSwitch(ovs33b).getStringId() );
		staticFlowEntryPusher.addFlow("reqLowerSw", ruleReqLowerSw, floodlightProvider.getSwitch(ovs34b).getStringId() );

		
		//---------------------------------------for all movie requests (higher switch)-----------------------------

		logger.debug("<<<<<<<<<<<<<<<MOVIE REQUESTS ON HIGHER SWITCHES>>>>>>>>>>>>");


		OFMatch matchReqHigherSw = new OFMatch();
		OFFlowMod ruleReqHigherSw = new OFFlowMod();
		ruleReqHigherSw.setType(OFType.FLOW_MOD);
		ruleReqHigherSw.setCommand(OFFlowMod.OFPFC_ADD);
		ruleReqHigherSw.setBufferId(OFPacketOut.BUFFER_ID_NONE);
		ruleReqHigherSw.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT);
		ruleReqHigherSw.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT);
		matchReqHigherSw.setDataLayerType(Ethernet.TYPE_IPv4);
		matchReqHigherSw.setNetworkProtocol(IPv4.PROTOCOL_UDP);
		matchReqHigherSw.setInputPort(OFPort.OFPP_LOCAL.getValue());
		//set everything to wildcards except nw_proto and dl_type
		matchReqHigherSw.setWildcards(~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_DL_TYPE);
		ruleReqHigherSw.setMatch(matchReqHigherSw);
		ArrayList<OFAction> reqHigherSwActions = new ArrayList<OFAction>();
		OFAction outReqHigherSw = new OFActionOutput((short)1);
		reqHigherSwActions.add(outReqHigherSw);
		ruleReqHigherSw.setActions(reqHigherSwActions);
		ruleReqHigherSw.setLengthU(OFFlowMod.MINIMUM_LENGTH
							+ OFActionOutput.MINIMUM_LENGTH );
		
		
		staticFlowEntryPusher.addFlow("reqHigherSw", ruleReqHigherSw, floodlightProvider.getSwitch(ovs11a).getStringId() );
		staticFlowEntryPusher.addFlow("reqHigherSw", ruleReqHigherSw, floodlightProvider.getSwitch(ovs21a).getStringId() );
		staticFlowEntryPusher.addFlow("reqHigherSw", ruleReqHigherSw, floodlightProvider.getSwitch(ovs22a).getStringId() );
		staticFlowEntryPusher.addFlow("reqHigherSw", ruleReqHigherSw, floodlightProvider.getSwitch(ovs31a).getStringId() );
		staticFlowEntryPusher.addFlow("reqHigherSw", ruleReqHigherSw, floodlightProvider.getSwitch(ovs32a).getStringId() );
		staticFlowEntryPusher.addFlow("reqHigherSw", ruleReqHigherSw, floodlightProvider.getSwitch(ovs33a).getStringId() );
		staticFlowEntryPusher.addFlow("reqHigherSw", ruleReqHigherSw, floodlightProvider.getSwitch(ovs34a).getStringId() );
		

		//---------------------------------------for UDP downstream movie (higher switch)-----------------------------
		
		logger.debug("<<<<<<<<<<<<<<<MOVIE FLOWS ON HIGHER SWITCHES>>>>>>>>>>>>");

		
		
		OFMatch matchMovieHigher = new OFMatch();
		OFFlowMod ruleMovieHigher = new OFFlowMod();
		ruleMovieHigher.setType(OFType.FLOW_MOD);
		ruleMovieHigher.setCommand(OFFlowMod.OFPFC_ADD);
		ruleMovieHigher.setBufferId(OFPacketOut.BUFFER_ID_NONE);
		ruleMovieHigher.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT);
		ruleMovieHigher.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT);
		matchMovieHigher.setDataLayerType(Ethernet.TYPE_IPv4);
		matchMovieHigher.setNetworkProtocol(IPv4.PROTOCOL_UDP);
		matchMovieHigher.setNetworkSource(IPv4.toIPv4Address(ROOT_IP));
		matchMovieHigher.setTransportSource((short) 33333);
		matchMovieHigher.setInputPort((short) 1);
		//set everything to wildcards except nw_proto and dl_type
		matchMovieHigher.setWildcards(~OFMatch.OFPFW_NW_PROTO 
								& ~OFMatch.OFPFW_DL_TYPE
								& ~OFMatch.OFPFW_NW_DST_ALL
								& ~OFMatch.OFPFW_TP_DST);
		ruleMovieHigher.setMatch(matchMovieHigher);
		ArrayList<OFAction> movieHigher = new ArrayList<OFAction>();
		OFAction outMovieHigher = new OFActionOutput(OFPort.OFPP_LOCAL.getValue());
		movieHigher.add(outMovieHigher);
		ruleMovieHigher.setActions(movieHigher);
		ruleMovieHigher.setLengthU(OFFlowMod.MINIMUM_LENGTH
							+ OFActionOutput.MINIMUM_LENGTH );
		
		
		staticFlowEntryPusher.addFlow("MovieHigher", ruleMovieHigher, floodlightProvider.getSwitch(ovs11a).getStringId() );
		staticFlowEntryPusher.addFlow("MovieHigher", ruleMovieHigher, floodlightProvider.getSwitch(ovs21a).getStringId() );
		staticFlowEntryPusher.addFlow("MovieHigher", ruleMovieHigher, floodlightProvider.getSwitch(ovs22a).getStringId() );
		staticFlowEntryPusher.addFlow("MovieHigher", ruleMovieHigher, floodlightProvider.getSwitch(ovs31a).getStringId() );
		staticFlowEntryPusher.addFlow("MovieHigher", ruleMovieHigher, floodlightProvider.getSwitch(ovs32a).getStringId() );
		staticFlowEntryPusher.addFlow("MovieHigher", ruleMovieHigher, floodlightProvider.getSwitch(ovs33a).getStringId() );
		staticFlowEntryPusher.addFlow("MovieHigher", ruleMovieHigher, floodlightProvider.getSwitch(ovs34a).getStringId() );
		
		
		//---------------------------------------for UDP downstream movie (lower switch)-----------------------------
		
		logger.debug("<<<<<<<<<<<<<<<MOVIE FLOWS ON LOWER SWITCHES>>>>>>>>>>>>");

		
		OFMatch matchMovieLower = new OFMatch();
		OFFlowMod ruleMovieLower = new OFFlowMod();
		ruleMovieLower.setType(OFType.FLOW_MOD);
		ruleMovieLower.setCommand(OFFlowMod.OFPFC_ADD);
		ruleMovieLower.setBufferId(OFPacketOut.BUFFER_ID_NONE);
		ruleMovieLower.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT);
		ruleMovieLower.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT);
		matchMovieLower.setDataLayerType(Ethernet.TYPE_IPv4);
		matchMovieLower.setNetworkProtocol(IPv4.PROTOCOL_UDP);
		matchMovieLower.setNetworkSource(IPv4.toIPv4Address(ROOT_IP));
		matchMovieLower.setTransportSource((short) 33333);
		matchMovieLower.setInputPort(OFPort.OFPP_LOCAL.getValue());
		//set everything to wildcards except nw_proto and dl_type
		matchMovieLower.setWildcards(~OFMatch.OFPFW_NW_PROTO 
									& ~OFMatch.OFPFW_DL_TYPE
									& ~OFMatch.OFPFW_NW_DST_ALL
									& ~OFMatch.OFPFW_TP_DST);
		ruleMovieLower.setMatch(matchMovieLower);
		ArrayList<OFAction> movieLower = new ArrayList<OFAction>();
		OFAction outMovieLower = new OFActionOutput((short) 1);
		movieLower.add(outMovieLower);
		ruleMovieLower.setActions(movieLower);
		ruleMovieLower.setLengthU(OFFlowMod.MINIMUM_LENGTH
								+ OFActionOutput.MINIMUM_LENGTH );
				
		//Added the rule for the main OVS on the source as well
		staticFlowEntryPusher.addFlow("MovieLower", ruleMovieLower, floodlightProvider.getSwitch(ovsMain).getStringId() );
		
		staticFlowEntryPusher.addFlow("MovieLower", ruleMovieLower, floodlightProvider.getSwitch(ovs11b).getStringId() );
		staticFlowEntryPusher.addFlow("MovieLower", ruleMovieLower, floodlightProvider.getSwitch(ovs21b).getStringId() );
		staticFlowEntryPusher.addFlow("MovieLower", ruleMovieLower, floodlightProvider.getSwitch(ovs22b).getStringId() );
		staticFlowEntryPusher.addFlow("MovieLower", ruleMovieLower, floodlightProvider.getSwitch(ovs31b).getStringId() );
		staticFlowEntryPusher.addFlow("MovieLower", ruleMovieLower, floodlightProvider.getSwitch(ovs32b).getStringId() );
		staticFlowEntryPusher.addFlow("MovieLower", ruleMovieLower, floodlightProvider.getSwitch(ovs33b).getStringId() );
		staticFlowEntryPusher.addFlow("MovieLower", ruleMovieLower, floodlightProvider.getSwitch(ovs34b).getStringId() );
					
					
				//---------------------------------------for backlogs (higher switch)-----------------------------
		
		logger.debug("<<<<<<<<<<<<<<<BACKLOGS ON HIGHER SWITCHES>>>>>>>>>>>>");

		OFMatch matchBacklogHigher = new OFMatch();
		OFFlowMod ruleBacklogHigher = new OFFlowMod();
		ruleBacklogHigher.setType(OFType.FLOW_MOD);
		ruleBacklogHigher.setCommand(OFFlowMod.OFPFC_ADD);
		ruleBacklogHigher.setBufferId(OFPacketOut.BUFFER_ID_NONE);
		ruleBacklogHigher.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT);
		ruleBacklogHigher.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT);
		matchBacklogHigher.setDataLayerType(Ethernet.TYPE_IPv4);
		matchBacklogHigher.setNetworkProtocol(IPv4.PROTOCOL_UDP);
		matchBacklogHigher.setNetworkSource(IPv4.toIPv4Address(ROOT_IP));
		matchBacklogHigher.setTransportSource((short) 44444);
		matchBacklogHigher.setInputPort((short) 1);
		//set everything to wildcards except nw_proto and dl_type
		matchBacklogHigher.setWildcards(~OFMatch.OFPFW_NW_PROTO 
										& ~OFMatch.OFPFW_DL_TYPE
										& ~OFMatch.OFPFW_NW_DST_ALL
										& ~OFMatch.OFPFW_TP_DST);
		ruleBacklogHigher.setMatch(matchBacklogHigher);
		ArrayList<OFAction> backlogHigher = new ArrayList<OFAction>();
		OFAction outBacklogHigher = new OFActionOutput(OFPort.OFPP_LOCAL.getValue());
		backlogHigher.add(outBacklogHigher);
		ruleBacklogHigher.setActions(backlogHigher);
		ruleBacklogHigher.setLengthU(OFFlowMod.MINIMUM_LENGTH
									+ OFActionOutput.MINIMUM_LENGTH );
				
				
		staticFlowEntryPusher.addFlow("BacklogHigher", ruleBacklogHigher, floodlightProvider.getSwitch(ovs11a).getStringId() );
		staticFlowEntryPusher.addFlow("BacklogHigher", ruleBacklogHigher, floodlightProvider.getSwitch(ovs21a).getStringId() );
		staticFlowEntryPusher.addFlow("BacklogHigher", ruleBacklogHigher, floodlightProvider.getSwitch(ovs22a).getStringId() );
		staticFlowEntryPusher.addFlow("BacklogHigher", ruleBacklogHigher, floodlightProvider.getSwitch(ovs31a).getStringId() );
		staticFlowEntryPusher.addFlow("BacklogHigher", ruleBacklogHigher, floodlightProvider.getSwitch(ovs32a).getStringId() );
		staticFlowEntryPusher.addFlow("BacklogHigher", ruleBacklogHigher, floodlightProvider.getSwitch(ovs33a).getStringId() );
		staticFlowEntryPusher.addFlow("BacklogHigher", ruleBacklogHigher, floodlightProvider.getSwitch(ovs34a).getStringId() );
				
				
		//---------------------------------------for backlogs (lower switch)-----------------------------
		
		logger.debug("<<<<<<<<<<<<<<<BACKLOGS ON LOWER SWITCHES>>>>>>>>>>>>");
		

		OFMatch matchBacklogLower = new OFMatch();
		OFFlowMod ruleBacklogLower = new OFFlowMod();
		ruleBacklogLower.setType(OFType.FLOW_MOD);
		ruleBacklogLower.setCommand(OFFlowMod.OFPFC_ADD);
		ruleBacklogLower.setBufferId(OFPacketOut.BUFFER_ID_NONE);
		ruleBacklogLower.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT);
		ruleBacklogLower.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT);
		matchBacklogLower.setDataLayerType(Ethernet.TYPE_IPv4);
		matchBacklogLower.setNetworkProtocol(IPv4.PROTOCOL_UDP);
		matchBacklogLower.setNetworkSource(IPv4.toIPv4Address(ROOT_IP));
		matchBacklogLower.setTransportSource((short) 44444);
		matchBacklogLower.setInputPort(OFPort.OFPP_LOCAL.getValue());
		//set everything to wildcards except nw_proto and dl_type
		matchBacklogLower.setWildcards(~OFMatch.OFPFW_NW_PROTO 
										& ~OFMatch.OFPFW_DL_TYPE
										& ~OFMatch.OFPFW_NW_DST_ALL
										& ~OFMatch.OFPFW_TP_DST);
		ruleBacklogLower.setMatch(matchBacklogLower);
		ArrayList<OFAction> backlogLower = new ArrayList<OFAction>();
		OFAction outBacklogLower = new OFActionOutput((short) 1);
		backlogLower.add(outBacklogLower);
		ruleBacklogLower.setActions(backlogLower);
		ruleBacklogLower.setLengthU(OFFlowMod.MINIMUM_LENGTH
									+ OFActionOutput.MINIMUM_LENGTH );
						
		//Added the rule for the main OVS on the source as well
		staticFlowEntryPusher.addFlow("BacklogLower", ruleBacklogLower, floodlightProvider.getSwitch(ovsMain).getStringId() );
						
		
		staticFlowEntryPusher.addFlow("BacklogLower", ruleBacklogLower, floodlightProvider.getSwitch(ovs11b).getStringId() );
		staticFlowEntryPusher.addFlow("BacklogLower", ruleBacklogLower, floodlightProvider.getSwitch(ovs21b).getStringId() );
		staticFlowEntryPusher.addFlow("BacklogLower", ruleBacklogLower, floodlightProvider.getSwitch(ovs22b).getStringId() );
		staticFlowEntryPusher.addFlow("BacklogLower", ruleBacklogLower, floodlightProvider.getSwitch(ovs31b).getStringId() );
		staticFlowEntryPusher.addFlow("BacklogLower", ruleBacklogLower, floodlightProvider.getSwitch(ovs32b).getStringId() );
		staticFlowEntryPusher.addFlow("BacklogLower", ruleBacklogLower, floodlightProvider.getSwitch(ovs33b).getStringId() );
		staticFlowEntryPusher.addFlow("BacklogLower", ruleBacklogLower, floodlightProvider.getSwitch(ovs34b).getStringId() );
						
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
