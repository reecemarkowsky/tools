import jenkins.model.Jenkins
import java.util.HashMap
import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import groovy.json.JsonSlurper

def firstRequestTemplate  = /
        {"q": "<%= metric %>{matrix_cluster_id:<%= cluster_id %>} ","style": {"width": "thick"},"type": "line"}/
def offsetRequestTemplate = /
        {"q": "timeshift(<%= metric %>{matrix_cluster_id:<%= cluster_id %>}, <%= offset %>)","style": {"type": "dotted","width": "normal"},"type": "line"}/

def firstSumWithRollupRequestTemplate  = /
        {"q": "sum:<%= metric %>{matrix_cluster_id:<%= cluster_id %>} by {matrix_cluster_id,flowid}.rollup(avg,60)","style": {"width": "normal"},"type": "line"}/
def offsetSumWithRollupRequestTemplate = /
        {"q": "timeshift(sum:<%= metric %>{matrix_cluster_id:<%= cluster_id %>} by {matrix_cluster_id,flowid}.rollup(avg,60), <%= offset %>)","style": {"type": "dotted", "width": "normal"},"type": "line"}/          

def firstMatLatency = / 
        {"q": "max:matrix.latency.histogram.p999{matrix_cluster_id:<%= cluster_id %>,latencymetric:connector.provider.marketing.cloud.send.latency} by {latencymetric}", "aggregator": "sum", "conditional_formats": [],"type": "line"},/
def firstStormLatency = / 
        {"q": "max:storm.matrix_latency_histogram_p999{matrix_cluster_id:<%= cluster_id %>} by {flowid}","aggregator": "sum", "conditional_formats": [], "type": "line" }/
def offsetMatLatency = / 
        {"q": "timeshift(max:matrix.latency.histogram.p999{matrix_cluster_id:<%= cluster_id %>,latencymetric:connector.provider.marketing.cloud.send.latency} by {latencymetric}, <%= offset %>)", "aggregator": "sum", "conditional_formats": [],"style": {"type": "dotted", "width": "normal"},"type": "line"},/
def offsetStormLatency = / 
        {"q": "timeshift(max:storm.matrix_latency_histogram_p999{matrix_cluster_id:<%= cluster_id %>} by {flowid}, <%= offset %>)","aggregator": "sum", "conditional_formats": [], "style": {"type": "dotted", "width": "normal"}, "type": "line"}/
          
def graphTemplate = /
        {
            "title": "<%= title %>",
            "definition": {
                "events": [],
                "requests": [ <%= requests %> ]},
                "viz": "timeseries"
        }/


def dashTemplate = /
  {
	"graphs": [ <%= graphs %>  ],
	"title": "<%= title %>",
	"description": "<%= title %>",
	"template_variables": [{
		"name": "host1",
		"prefix": "host",
		"default": "host:my-host"
	}]
}/

def buildParams = { bld ->
    if (bld == 3 ) {
      return ["Thunder3b", "xyfpt041j95e", 1458897292, 1458900892]
    }
    println "retrieving parameters for build: " + bld
    hudson.model.FreeStyleBuild build = project.getBuildByNumber(bld)
    hudson.EnvVars vars = build.getEnvironment()
    File logFile = build.getLogFile()
    start_pattern = /^XXX2016.*\s+starts on\s(.*)\./
    alt_start_pattern = /^2016.*LIST Stream.*$/
    end_pattern = /^2016.*\s+passed on\s(.*)\./
    finished_pattern = /^2016.*Done\.$/
    date_pattern = /(^2016-[0-9]{2}-[0-9]{2})T([0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3})Z.*$/
    matrix_cluster_id = null

    logFile.eachLine { line ->
        def m = line =~ start_pattern        
        if (m.getCount()) {
            matrix_cluster_id = m[0][1]
            n = line =~ date_pattern
            start_date = n[0][1]
            start_time = n[0][2]
            println "start_date" + start_date
            println "start_time" + start_time
        }
      
        def y = line =~ alt_start_pattern        
        if (y.getCount()) {
            n = line =~ date_pattern
            start_date = n[0][1]
            start_time = n[0][2]
            println "start_date:" + start_date
            println "start_time:" + start_time
            println "line used : " + line
         }
        
      
        e = line =~ end_pattern
        if (e.getCount()) {
            matrix_cluster_id = e[0][1]
        }
      
        f = line =~ finished_pattern
        if (f.getCount()) {
            n = line =~ date_pattern
            end_date = n[0][1]
            end_time = n[0][2]
            println "end_date :" + end_date
            println "end_time :" + end_time
            println "line used: " + line
        }
        
    }
   
    Date buildStartDate = new Date().parse("y-M-d k:m:s.S",start_date + " " + start_time)
    Date buildEndDate = new Date().parse("y-M-d k:m:s.S",end_date + " " + end_time)
    println "BUILD Start Date: " + buildStartDate
    println "BUILD End   Date: " + buildEndDate
    def start = String.valueOf(buildStartDate.getTime())
    def end = String.valueOf(buildEndDate.getTime())
    [bld, matrix_cluster_id, start, end]
}

def fillTemplate = { Map bindings, String jsonTemplate ->
    SimpleTemplateEngine simpleTemplateEngine = new SimpleTemplateEngine();
    Template template = simpleTemplateEngine.createTemplate(jsonTemplate);
    Writable writable = template.make(bindings);
    writable.toString();
}

buildsToCompareTokens = new String(BUILDS_TO_COMPARE)
println "build numbers" + buildsToCompareTokens
project = Jenkins.instance.getItem(PROJECT_NAME)
println "project:" + project
metrics = new String(METRICS)
println "metrics: " + metrics
buildNums = buildsToCompareTokens.tokenize( ',' ).collect{ it.toInteger() }
graphs = buildNums.collect { buildParams(it) }
mostRecentStartTime = graphs[0][2]
println mostRecentStartTime
graphsWithOffsets = graphs.collect { it.plus(new BigInteger(it[2]).intdiv(1000) - new BigInteger(mostRecentStartTime).intdiv(1000)) }
String graphs = ""
metricList = metrics.tokenize( ',' )

for (String metric: metricList) {
    println "LOOKING AT " + metric
    title = metric
    metricName = metric
    if (metric.contains(":")) {
        metric_and_title = metric.tokenize( ':' )
        metricName = metric_and_title[0]
        title = metric_and_title[1]
    }
    String requests = ""
    println graphsWithOffsets.toString()
    for (int i=0; i < graphsWithOffsets.size(); i++) {
        println "i=" + i
        Map reqBindings = new HashMap()
        reqBindings.put("metric", metricName)
        reqBindings.put("cluster_id", graphsWithOffsets[i][1])
        reqBindings.put("offset",graphsWithOffsets[i][4])
        if (i == 0) {
          if (metricName.contains("matrix_pipelines_messages_received")) {
             requests += fillTemplate(reqBindings,firstSumWithRollupRequestTemplate)
          } else if (metricName.contains("histogram") ) {
            requests += fillTemplate(reqBindings,firstMatLatency)
            requests += fillTemplate(reqBindings,firstStormLatency)  
          } else {
            requests += fillTemplate(reqBindings,firstRequestTemplate)
          }
        } else {
          if (metricName.contains("matrix_pipelines_messages_received")) {
             requests += fillTemplate(reqBindings,offsetSumWithRollupRequestTemplate)
          } else if (metricName.contains("histogram") ) {
            requests += fillTemplate(reqBindings,offsetMatLatency)
            requests += fillTemplate(reqBindings,offsetStormLatency)
          } else {
            requests += fillTemplate(reqBindings,offsetRequestTemplate)
          }
        }
        if (i < graphsWithOffsets.size() - 1) {
            requests += ","
        }
    }
    Map graphBindings = new HashMap()
    graphBindings.put("title",title)
    graphBindings.put("requests",requests)
    graph = fillTemplate(graphBindings,graphTemplate)
    graphs += graph
    if (metric != metricList.last()) {
        graphs += ","
    }
}

Map dashBindings = new HashMap()
dashBindings.put("title",project.getName() + " perf analysis for builds:" + buildsToCompareTokens)
dashBindings.put("graphs",graphs)

dashboard = fillTemplate(dashBindings,dashTemplate)
println dashboard
def response = ["curl", "-k", "-X", "POST", "-H", "Content-Type: application/json", "-d", "${dashboard}", "https://app.datadoghq.com/api/v1/dash?api_key=3cdcb4712c44d5c92d991e538401a1b4&application_key=b755858c86e04b0919392cc99dfab78e736c8747"].execute().text
println response
def jsonSlurper = new JsonSlurper()
def result = jsonSlurper.parseText(response)
println result
firstGraph = graphsWithOffsets.first()
lastGraph = graphsWithOffsets.last()
println "https://app.datadoghq.com" + result.url + '?live=false&page=0&is_auto=false&from_ts=' + firstGraph[2] + '&to_ts=' + firstGraph[3] + '&tile_size=xl'

