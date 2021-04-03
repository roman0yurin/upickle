

lazy val upickleReadme = scalatex.ScalatexReadme(
  projectId = "upickleReadme",
  wd = file(""),
  url = "https://github.com/lihaoyi/upickle/tree/master",
  source = "Readme"
).settings(
  scalaVersion := "2.12.7"
)

val printDependencyClasspath = taskKey[Unit]("Prints location of the dependencies")

printDependencyClasspath := {
	val cp = (dependencyClasspath in Compile).value
	cp.foreach(f => println(s"${f.metadata.get(moduleID.key)} => ${f.data}"))
}