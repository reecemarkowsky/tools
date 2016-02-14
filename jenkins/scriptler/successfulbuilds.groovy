import jenkins.model.Jenkins
import java.util.TreeMap
java.util.Map map = new java.util.TreeMap()
project = Jenkins.instance.getItem(PROJECT_NAME)
successfulBuilds = project.getBuilds().findAll({ it.getResult().toString().equals("SUCCESS") })
for (hudson.model.FreeStyleBuild build : successfulBuilds) {
  map.put(build.getNumber(), build.toString())  
}

NavigableMap nmap=map.descendingMap();
return nmap
