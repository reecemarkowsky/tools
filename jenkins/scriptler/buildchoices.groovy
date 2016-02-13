import jenkins.model.Jenkins
import java.util.HashMap
import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import groovy.json.JsonSlurper

buildsToCompareTokens = new String(BUILDS_TO_COMPARE)
println buildsToCompareTokens
buildsToCompare = buildsToCompareTokens.tokenize( ',' )
a_build = buildsToCompare[1].toInteger()
b_build = buildsToCompare[0].toInteger()

project = Jenkins.instance.getItem(PERF_TEST)
def builds = project.getBuilds()
HashMap map = new HashMap()
for (hudson.model.FreeStyleBuild item : builds) {
  map.put(item.toString(),item.getNumber())
  }

def buildParams = { int bld ->  
	hudson.model.FreeStyleBuild build = project.getBuildByNumber(bld)
	hudson.EnvVars vars = build.getEnvironment()
	File logFile = build.getLogFile()
	start_pattern = /^2016.*\s+starts on\s(.*)\./
    end_pattern = /^2016.*\s+passed on\s(.*)\./
	date_pattern = /(^2016-[0-9]{2}-[0-9]{2})T([0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3})Z.*\./
	def matrix_cluster_id = null
	def start_time = null
	def start_date = null
    def end_time = null
    def end_date = null

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
    println start
    println end
    [matrix_cluster_id, start, end] 
}  

buildA = buildParams(a_build)
buildB = buildParams(b_build)
offset = buildB[1].toLong().intdiv(1000) - buildA[1].toLong().intdiv(1000)
println offset 

Map bindings = new HashMap();
bindings.put("title", "agent-smith-perf-test-ms-nofilter-bullets Build:"+a_build+" vs Build:"+b_build)
bindings.put("cluster_id_a", buildA[0]);
bindings.put("cluster_id_b", buildB[0]);
bindings.put("offset",String.valueOf(offset));


def jsonTemplate = /
  {
	"graphs": [{
		"title": "Ingestion 5m Rate",
		"definition": {
			"events": [],
			"requests": [{
				"q": "ingestion.events.ingested.m5_rate{matrix_cluster_id:<%= cluster_id_a %>}",
				"style": {
					"width": "thick"
				},
				"type": "line"
			}, {
				"q": "timeshift(ingestion.events.ingested.m5_rate{matrix_cluster_id:<%= cluster_id_b %>}, <%= offset %>)",
				"style": {
					"type": "dotted",
					"width": "normal"
				},
				"type": "line"
			}]
		},
		"viz": "timeseries"
	},{
		"title": "Matrix End-End Latency p99.9",
		"definition": {
			"events": [],
			"requests": [{
				"q": "matrix.latency.histogram.p999{matrix_cluster_id:<%= cluster_id_a %>}",
				"style": {
					"width": "thick"
				},
				"type": "line"
			}, {
				"q": "timeshift(matrix.latency.histogram.p999{matrix_cluster_id:<%= cluster_id_b %>}, <%= offset %>)",
				"style": {
					"type": "dotted",
					"width": "normal"
				},
				"type": "line"
			}]
		},
		"viz": "timeseries"
	},{
		"title": "Throughput Orchestrations",
		"definition": {
			"events": [],
			"requests": [{
				"q": "storm.matrix_pipelines_messages_received{matrix_cluster_id:<%= cluster_id_a %>}",
				"style": {
					"width": "thick"
				},
				"type": "line"
			}, {
				"q": "timeshift(storm.matrix_pipelines_messages_received{matrix_cluster_id:<%= cluster_id_b %>}, <%= offset %>)",
				"style": {
					"type": "dotted",
					"width": "normal"
				},
				"type": "line"
			}]
		},
		"viz": "timeseries"
	}
              ],
	"title": "<%= title %>",
	"description": "<%= title %>",
	"template_variables": [{
		"name": "host1",
		"prefix": "host",
		"default": "host:my-host"
	}]
}/

SimpleTemplateEngine simpleTemplateEngine = new SimpleTemplateEngine();
Template template = simpleTemplateEngine.createTemplate(jsonTemplate);
Writable writable = template.make(bindings);
String jsonPublish = writable.toString();
    
def response = ["curl", "-k", "-X", "POST", "-H", "Content-Type: application/json", "-d", "${jsonPublish}", "https://app.datadoghq.com/api/v1/dash?api_key=40b9a1db96b8dd5b12083e228f9e1b62&application_key=b755858c86e04b0919392cc99dfab78e736c8747"].execute().text

def jsonSlurper = new JsonSlurper()
def result = jsonSlurper.parseText(response)
println "https://app.datadoghq.com" + result.url + '?live=false&page=0&is_auto=false&from_ts=' + buildB[1] + '&to_ts=' + buildA[2] + '&tile_size=m'
