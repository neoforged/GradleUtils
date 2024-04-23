/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils

import groovy.transform.CompileStatic
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPomDeveloper
import org.gradle.api.publish.maven.MavenPomLicense

@CompileStatic
class PomUtilsExtension {
    void license(MavenPom pom, License license) {
        pom.licenses {
            it.license { MavenPomLicense l ->
                l.name.set(license.name)
                l.url.set(license.url)
            }
        }
    }

    void neoForgedDeveloper(MavenPom pom) {
        pom.developers {
            it.developer { MavenPomDeveloper dev ->
                dev.id.set('neoforged')
                dev.name.set('NeoForged')
                dev.email.set('contact@neoforged.net')
                dev.url.set('https://github.com/neoforged')
            }
        }
    }

    void githubRepo(MavenPom pom, String repo, String org = 'NeoForged') {
        pom.url.set("https://github.com/$org/$repo" as String)
        pom.scm { scm ->
            scm.url.set("https://github.com/$org/$repo" as String)
            scm.connection.set("scm:git:git://github.com/$org/${repo}.git" as String)
            scm.developerConnection.set("scm:git:git@github.com:$org/${repo}.git" as String)
        }

        pom.issueManagement { issues ->
            issues.system.set('github')
            issues.url.set("https://github.com/$org/$repo/issues" as String)
        }
    }

    enum License {
        LGPL_v2('LGPL-2.1-only'),
        MIT('MIT');

        final String name, url

        License(String name) {
            this.name = name
            this.url = "https://spdx.org/licenses/${name}"
        }
    }
}
