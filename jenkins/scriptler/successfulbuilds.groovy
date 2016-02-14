import jenkins.model.Jenkins
import java.util.TreeMap
java.util.Map map = new java.util.TreeMap()
project = Jenkins.instance.getItem('agent-smith-perf-test-ms')
successfulBuilds = project.getBuilds().findAll({ it.getResult().toString().equals("SUCCESS") }).reverse()
for (hudson.model.FreeStyleBuild build : successfulBuilds) {
  map.put(build.getNumber(), build)
}
NavigableMap nmap=map.descendingMap();
return nmap
