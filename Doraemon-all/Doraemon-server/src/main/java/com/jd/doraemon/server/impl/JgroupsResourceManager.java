/**
 * 
 */
package com.jd.doraemon.server.impl;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.jgroups.JChannel;

import com.jd.doraemon.core.cluster.GroupClusters;
import com.jd.doraemon.core.cluster.ServerInfo;
import com.jd.doraemon.core.cluster.ServerInfo.ServerType;
import com.jd.doraemon.server.ContainerUtils;
import com.jd.doraemon.server.ResourceManager;
import com.jd.doraemon.server.ServerContainer;
import com.jd.doraemon.server.rpc.ServerRpcInvoker;

/**
 * @author luolishu
 * 
 */
public class JgroupsResourceManager implements ResourceManager {
	protected long initialDelay = 5000;
	protected long synPeriodTime = 300;
	protected long synServerPeriodTime = 300;
	protected ScheduledExecutorService scheduledThreadPool = Executors
			.newSingleThreadScheduledExecutor(new ThreadFactory() {

				@Override
				public Thread newThread(Runnable r) {
					Thread thread = new Thread(r);
					thread.setDaemon(true);
					thread.setName("syn_server_list");
					return thread;
				}

			});

	@Override
	public void addGroup(String group) {
		try {
			this.createGroupMessageChannel(group);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			this.createGroupRpcChannel(group);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		this.createRpcInvoker(group);
		this.addGroupServerInfo(group);
	}

	@Override
	public void init() {
		try {
			initMessageGroupChannels();
			initRpcGroupChannels();
			initClusters();
			initSysnServerInfoTask();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private void initSysnServerInfoTask() {
		ServerListTask command = new ServerListTask();
		scheduledThreadPool.scheduleAtFixedRate(command, initialDelay,
				synServerPeriodTime, TimeUnit.MILLISECONDS);
	}

	protected ServerInfo collectServerInfo() {
		ServerInfo serverInfo = new ServerInfo(ServerType.SEVER);

		try {
			InetAddress inetAddress = InetAddress.getLocalHost();
			serverInfo.setIpAddress(inetAddress);
			serverInfo.setHostName(inetAddress.getHostName());
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return serverInfo;
	}

	private void initClusters() {

		ServerContainer container = ContainerUtils.getContainer();
		Set<String> groups = container.getGroupNames();
		for (String group : groups) {
			if (groupClustersMap.containsKey(group)) {
				continue;
			}
			this.addGroupServerInfo(group);
		}
	}

	private void addGroupServerInfo(String group) {
		ServerInfo serverInfo = this.collectServerInfo();
		serverInfo.setMaster(true);
		GroupClusters groupClusters = new GroupClusters();
		groupClusters.setName(group);
		groupClusters.setSelf(serverInfo);
		serverInfo.setAddress(RPC_CHANNELS_MAP.get(group).getAddress());
		groupClusters.addConfigSever(serverInfo);
		groupClusters.setMaster(serverInfo);
		groupClustersMap.put(group, groupClusters);
	}

	private void createRpcInvoker(String group) {
		if (rpcInvokerMap.containsKey(group))
			return;
		JChannel channel = RPC_CHANNELS_MAP.get(group);
		ServerRpcInvoker invoker = new ServerRpcInvoker(channel);
		rpcInvokerMap.put(group, invoker);
	}

	private JChannel createChannel(String channelName, String clusterName)
			throws Exception {
		JChannel jchannel = new JChannel("mping2.xml");
		jchannel.setName(channelName);
		jchannel.connect(clusterName);
		jchannel.setDiscardOwnMessages(true);
		return jchannel;
	}

	private void createGroupMessageChannel(String group) throws Exception {
		if (GROUP_MESSAGE_CHANNEL_MAP.containsKey(group)) {
			return;
		}
		ServerContainer container = ContainerUtils.getContainer();
		String serverName = container.getServerName();
		String channelName = "sever." + group;
		String clusterName = serverName + "." + group;
		JChannel jchannel = createChannel(channelName, clusterName);
		GROUP_MESSAGE_CHANNEL_MAP.put(group, jchannel);
	}

	private void createGroupRpcChannel(String group) throws Exception {
		if (RPC_CHANNELS_MAP.containsKey(group)) {
			return;
		}
		ServerContainer container = ContainerUtils.getContainer();
		String serverName = container.getServerName();
		String channelName = "sever.rpc." + group;
		String clusterName = serverName + "." + group;
		JChannel jchannel = createChannel(channelName, clusterName);
		jchannel = createChannel(channelName, clusterName);
		RPC_CHANNELS_MAP.put(group, jchannel);
	}

	private void initMessageGroupChannels() throws Exception {
		ServerContainer container = ContainerUtils.getContainer();
		Set<String> groups = container.getGroupNames();

		for (String group : groups) {
			this.createGroupMessageChannel(group);
		}
	}

	private void initRpcGroupChannels() throws Exception {
		ServerContainer container = ContainerUtils.getContainer();
		Set<String> groups = container.getGroupNames();
		for (String group : groups) {
			this.createGroupRpcChannel(group);
		}
	}

	private class ServerListTask implements Runnable {

		@Override
		public void run() {
			Set<Entry<String, ServerRpcInvoker>> invokerEntrys = rpcInvokerMap
					.entrySet();
			for (Entry<String, ServerRpcInvoker> entry : invokerEntrys) {
				String group = entry.getKey();
				ServerRpcInvoker invoker = entry.getValue();
				List<ServerInfo> serverInfoList = invoker
						.getAllServerInfos(group);

				if (serverInfoList == null)
					return;
				GroupClusters clusters = groupClustersMap.get(group);
				for (ServerInfo server : serverInfoList) {
					if (server == null) {
						continue;
					}

					if (server.isClient()) {
						clusters.addConfigClient(server);
					}
					if (server.isServer()) {
						clusters.addConfigSever(server);
					}
					if (server.isMaster()) {
						clusters.setMaster(server);
					}
				}
				if (clusters.getMaster() == null
						&& !clusters.getServers().isEmpty()) {
					clusters.setMaster(clusters.getServers().iterator().next());
				}
			}

		}

	}

}
