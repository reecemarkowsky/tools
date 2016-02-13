import jenkins.model.Jenkins
import java.util.HashMap
project = Jenkins.instance.getItem(PERF_TEST)
def builds = project.getBuilds()
java.util.Map map = new java.util.HashMap()
for (hudson.model.FreeStyleBuild item : builds) {
   if (item.getResult().toString().equals("SUCCESS")) {
       map.put(item.getNumber(),item.toString())
    }
 }
return map
