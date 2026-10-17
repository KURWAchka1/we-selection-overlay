export const dispatchTraceTargets = Object.freeze([
  'org.marshal.weaddon_lightposition.bridge.server.BridgeServer::process',
  'org.marshal.weaddon_lightposition.bridge.paper.WorldEditOverlayBridgePlugin::sendReply',
  'org.marshal.weaddon_lightposition.bridge.paper.WorldEditOverlayBridgePlugin$SelectionHost::mayReadSelection',
  'org.marshal.weaddon_lightposition.bridge.paper.WorldEditOverlayBridgePlugin$SelectionHost::mayEditSelection',
  'org.marshal.weaddon_lightposition.bridge.paper.WorldEditOverlayBridgePlugin$SelectionHost::captureEdit',
  'org.marshal.weaddon_lightposition.bridge.paper.WorldEditOverlayBridgePlugin$SelectionHost::applyEdit',
  'org.bukkit.craftbukkit.entity.CraftPlayer::sendPluginMessage',
  'org.bukkit.plugin.messaging.StandardMessenger::validatePluginMessage',
  'org.marshal.wesotest.slices.WindowsDispatchCounter::read',
]);
export const dispatchStallEvents = Object.freeze([
  'jdk.JavaMonitorEnter', 'jdk.JavaMonitorWait', 'jdk.ThreadPark', 'jdk.ThreadSleep',
  'jdk.FileRead', 'jdk.FileWrite', 'jdk.SocketRead', 'jdk.SocketWrite',
]);
