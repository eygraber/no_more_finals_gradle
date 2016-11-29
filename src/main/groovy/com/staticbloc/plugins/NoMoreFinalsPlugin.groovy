package com.staticbloc.plugins

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class NoMoreFinalsPlugin implements Plugin<Project> {
  @Override
  void apply(final Project project) {
    if (project.hasProperty('android')) {
      project.extensions.create('no_more_finals', NoMoreFinalsExtension)
      project.android.registerTransform(new NoMoreFinalsTransform(project))
    }
    else {
      throw new GradleException("NoMoreFinalsPlugin must be applied *AFTER* Android plugin")
    }
  }
}