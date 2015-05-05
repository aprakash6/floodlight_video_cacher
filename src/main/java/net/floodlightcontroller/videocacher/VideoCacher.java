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


public class VideoCacher implements IFloodlightModule, IOFMessageListener {

	
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
	
	protected Map<TableEntry, byte[]> macTable;
	
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
	    logger = LoggerFactory.getLogger(VideoCacher.class);
	    macTable = new HashMap <TableEntry, byte[]>();

		
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		
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
	
	private Command newRequestFromClient(IOFSwitch sw, OFPacketIn pi) {
		 
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
        
        if(!macTable.containsKey(ipPortEntry))
        {
        	macTable.put(ipPortEntry, match.getDataLayerSource());
        }
       
        
		//if ( sw.getId() == Long.valueOf(childSw).longValue() ) {
			
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
		//}
		
	
 		/*	
		
		if ( sw.getId() == Long.valueOf(rootSw).longValue() ) {
			
			 Short outPort2 = ;
			 
			 
			//add flow rule in child switch to duplicate flows
			
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
 			
 			
 			
 			//************SET OUTPUT PORT HERE*******************
 			OFAction out1 = new OFActionOutput(outPort2);
 			
 			OFActionDataLayerSource dlSrc = new OFActionDataLayerSource();
 			OFActionDataLayerDestination dlDst = new OFActionDataLayerDestination();
 			OFActionNetworkLayerSource nwSrc = new OFActionNetworkLayerSource();
 			OFActionNetworkLayerDestination nwDst = new OFActionNetworkLayerDestination();
 			OFActionTransportLayerSource tlSrc = new OFActionTransportLayerSource();
 			OFActionTransportLayerDestination tlDst = new OFActionTransportLayerDestination();
 			
 			dlSrc.setDataLayerAddress(Ethernet.toMACAddress(CHILD_UP_MAC));
 			dlDst.setDataLayerAddress(Ethernet.toMACAddress(ROOT_MAC));
 			
 			nwSrc.setNetworkAddress(IPv4.toIPv4Address(CHILD_UP_IP));
 			nwDst.setNetworkAddress(IPv4.toIPv4Address(ROOT_IP));
 			
 			actions.add(dlSrc);
 			actions.add(dlDst);
 			actions.add(nwSrc);
 			actions.add(nwDst);
 			//actions.add(tlSrc);
 			//actions.add(tlDst);
 			actions.add(out1);
 			
 			rule.setActions(actions);
 			
 			//logger.warn(actions.toString());
 			
 			//specify the length of the flow structure created
 			//rule.setLength((short) (OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH)); 			
 			
 			int actionsLength = ( OFActionOutput.MINIMUM_LENGTH + 
 								  OFActionDataLayerSource.MINIMUM_LENGTH + 
 								  OFActionDataLayerDestination.MINIMUM_LENGTH + 
 								  OFActionNetworkLayerSource.MINIMUM_LENGTH + 
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
		}
		
		*/
 			
		return Command.CONTINUE;
	}
	
	

}
