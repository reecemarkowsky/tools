import jenkins.model.Jenkins
import java.util.HashMap
import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import groovy.json.JsonSlurper

def firstRequestTemplate  = /
        {"q": "<%= metric %>{matrix_cluster_id:<%= cluster_id %>}","style": {"width": "thick"},"type": "line"}/
def offsetRequestTemplate = /
        {"q": "timeshift(<%= metric %>{matrix_cluster_id:<%= cluster_id %>}, <%= offset %>)","style": {"type": "dotted","width": "normal"},"type": "line"}/
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
    println "retrieving parameters for build: " + bld
    hudson.model.FreeStyleBuild build = project.getBuildByNumber(bld)
    hudson.EnvVars vars = build.getEnvironment()
    File logFile = build.getLogFile()
    start_pattern = /^2016.*\s+starts on\s(.*)\./
    end_pattern = /^2016.*\s+passed on\s(.*)\./
    date_pattern = /(^2016-[0-9]{2}-[0-9]{2})T([0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3})Z.*\./

    logFile.eachLine { line ->
        def m = line =~ start_pattern
        if (m.getCount()) {
            matrix_cluster_id = m[0][1]
            n = line =~ date_pattern
            start_date = n[0][1]
            start_time = n[0][2]
        }
        m = line =~ end_pattern
        if (m.getCount()) {
            n = line =~ date_pattern
            end_date = n[0][1]
            end_time = n[0][2]
        }
    }
    Date buildStartDate = new Date().parse("y-M-d k:m:s.S",start_date + " " + start_time)
    Date buildEndDate = new Date().parse("y-M-d k:m:s.S",end_date + " " + end_time)
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
    String requests = ""
    for (int i=0; i < graphsWithOffsets.size(); i++) {
        Map reqBindings = new HashMap()
        reqBindings.put("metric", metric)
        reqBindings.put("cluster_id", graphsWithOffsets[i][1])
        reqBindings.put("offset",graphsWithOffsets[i][4])
        if (i == 0) {
            requests += fillTemplate(reqBindings,firstRequestTemplate)
        } else {
            requests += fillTemplate(reqBindings,offsetRequestTemplate)
        }
        if (i < graphsWithOffsets.size() - 1) {
            requests += ","
        }
    }
    Map graphBindings = new HashMap()
    graphBindings.put("title",metric)
    graphBindings.put("requests",requests)
    graph = fillTemplate(graphBindings,graphTemplate)
    graphs += graph
    if (metric != metricList.last()) {
        graphs += ","
    }
}

Map dashBindings = new HashMap()
dashBindings.put("title","Agentsmith Perf Comparison")
dashBindings.put("graphs",graphs)

dashboard = fillTemplate(dashBindings,dashTemplate)
def response = ["curl", "-k", "-X", "POST", "-H", "Content-Type: application/json", "-d", "${dashboard}", "https://app.datadoghq.com/api/v1/dash?api_key=40b9a1db96b8dd5b12083e228f9e1b62&application_key=b755858c86e04b0919392cc99dfab78e736c8747"].execute().text

def jsonSlurper = new JsonSlurper()
def result = jsonSlurper.parseText(response)
firstGraph = graphsWithOffsets.last()
lastGraph = graphsWithOffsets.first()
println "https://app.datadoghq.com" + result.url + '?live=false&page=0&is_auto=false&from_ts=' + firstGraph[2] + '&to_ts=' + lastGraph[3] + '&tile_size=m'
