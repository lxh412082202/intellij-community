package org.jetbrains.plugins.settingsRepository.git

import org.eclipse.jgit.transport.URIish
import org.jetbrains.plugins.settingsRepository.Credentials
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.settingsRepository.BaseRepositoryManager
import com.intellij.execution.process.ProcessNotCreatedException

private var canUseGitExe = true

// https://www.kernel.org/pub/software/scm/git/docs/git-credential.html
fun getCredentialsUsingGit(uri: URIish): Credentials? {
  if (!canUseGitExe) {
    return null
  }

  val commandLine = GeneralCommandLine()
  commandLine.setExePath("git")
  commandLine.addParameter("credential")
  commandLine.addParameter("fill")
  commandLine.setPassParentEnvironment(true)
  val process: Process
  try {
    process = commandLine.createProcess()
  }
  catch(e: ProcessNotCreatedException) {
    canUseGitExe = false
    return null
  }

  val writer = process.getOutputStream().buffered().writer()
  writer.write("url=")
  writer.write(uri.toPrivateString())
  writer.write("\n\n")
  writer.close();

  val reader = process.getInputStream().reader().buffered()
  var username: String? = null
  var password: String? = null
  while (true) {
    val line = reader.readLine()?.trim()
    if (line == null || line.isEmpty()) {
      break
    }

    fun readValue() = line.substring(line.indexOf('=') + 1).trim()

    if (line.startsWith("username=")) {
      username = readValue()
    }
    else if (line.startsWith("password=")) {
      password = readValue()
    }
  }
  reader.close()

  val errorText = process.getErrorStream().reader().readText()
  if (!StringUtil.isEmpty(errorText)) {
    BaseRepositoryManager.LOG.warn(errorText)
  }
  return if (username == null && password == null) null else Credentials(username, password)
}