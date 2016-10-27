import groovy.json.JsonSlurper
import java.util.HashMap
java.util.Map monitorMap = new java.util.HashMap()
def ddUrl="https://app.datadoghq.com/api/v1/monitor?api_key=" + API_KEY + "&application_key=" + APP_KEY
ddMonitors = ddUrl.toURL().text
def jsonSlurper = new JsonSlurper()
def dd_monitors = jsonSlurper.parseText(ddMonitors)
dd_monitors.each { 
  monitorMap.put(it.id, it.name) }
monitorMap
