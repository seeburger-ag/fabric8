package io.fabric8.dosgi.util;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZookeeperBootstrap {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ZookeeperBootstrap.class);
	
	public ZooKeeperServer activate(int serverPort, int clientPort) throws Exception {
		
		Properties clientProps = new Properties();
		clientProps.setProperty("clientPort", String.valueOf(serverPort));
		clientProps.setProperty("dataDir", System.getProperty("karaf.data","data")+"/zookeeper/data");
		File dataLogDir = new File(System.getProperty("karaf.data","data")+"/log/zookeeper/data");
		dataLogDir.mkdirs();
		clientProps.setProperty("dataLogDir", dataLogDir.getPath());
		ServerConfig serverConfig = new ServerConfig();
		QuorumPeerConfig peerConfig = new QuorumPeerConfig();
		peerConfig.parseProperties(clientProps);
		serverConfig.readFrom(peerConfig);
//		if(available(serverPort))
//		{
			
			
			ZooKeeperServer zkServer = new ZooKeeperServer();
			FileTxnSnapLog ftxn = new FileTxnSnapLog(new File(serverConfig.getDataLogDir()), new File(serverConfig.getDataDir()));
			zkServer.setTxnLogFactory(ftxn);
			zkServer.setTickTime(serverConfig.getTickTime());
			zkServer.setMinSessionTimeout(serverConfig.getMinSessionTimeout());
			zkServer.setMaxSessionTimeout(serverConfig.getMaxSessionTimeout());
			NIOServerCnxnFactory cnxnFactory = new NIOServerCnxnFactory() {
				protected void configureSaslLogin() throws IOException {
				}
			};
			cnxnFactory.configure(serverConfig.getClientPortAddress(), serverConfig.getMaxClientCnxns());
			
			try {
				LOGGER.debug("Starting ZooKeeper server on address %s", peerConfig.getClientPortAddress());
				cnxnFactory.startup(zkServer);
				LOGGER.debug("Started ZooKeeper server");
			} catch (Exception e) {
				LOGGER.warn(String.format("Failed to start ZooKeeper server, reason : %s", e));
				cnxnFactory.shutdown();
				throw e;
			}
			return zkServer;
//		}
//		else
//		{
//            NIOServerCnxnFactory cnxnFactory = new NIOServerCnxnFactory();
//            cnxnFactory.configure(peerConfig.getClientPortAddress(), peerConfig.getMaxClientCnxns());
//
//            QuorumPeer quorumPeer = new QuorumPeer();
//            quorumPeer.setClientPortAddress(peerConfig.getClientPortAddress());
//            quorumPeer.setTxnFactory(new FileTxnSnapLog(new File(peerConfig.getDataLogDir()), new File(peerConfig.getDataDir())));
//            quorumPeer.setQuorumPeers(peerConfig.getServers());
//            quorumPeer.setElectionType(peerConfig.getElectionAlg());
//            quorumPeer.setMyid(peerConfig.getServerId());
//            quorumPeer.setTickTime(peerConfig.getTickTime());
//            quorumPeer.setMinSessionTimeout(peerConfig.getMinSessionTimeout());
//            quorumPeer.setMaxSessionTimeout(peerConfig.getMaxSessionTimeout());
//            quorumPeer.setInitLimit(peerConfig.getInitLimit());
//            quorumPeer.setSyncLimit(peerConfig.getSyncLimit());
//            quorumPeer.setQuorumVerifier(peerConfig.getQuorumVerifier());
//            quorumPeer.setCnxnFactory(cnxnFactory);
//            quorumPeer.setZKDatabase(new ZKDatabase(quorumPeer.getTxnFactory()));
//            quorumPeer.setLearnerType(peerConfig.getPeerType());
//
//            try {
//                LOGGER.debug("Starting quorum peer \"%s\" on address %s", quorumPeer.getMyid(), peerConfig.getClientPortAddress());
//                quorumPeer.start();
//                LOGGER.debug("Started quorum peer \"%s\"", quorumPeer.getMyid());
//            } catch (Exception e) {
//                LOGGER.warn(String.format("Failed to start quorum peer \"%s\", reason : %s ", quorumPeer.getMyid(), e.getMessage()));
//                quorumPeer.shutdown();
//                throw e;
//            }
//		}
	}
	

}

