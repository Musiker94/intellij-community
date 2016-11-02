/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.codeInsight.hints.MethodInfo
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil

class GroovyInlayParameterHintsProvider : InlayParameterHintsProvider {

  private companion object {
    val blackList = setOf(
        "org.codehaus.groovy.runtime.DefaultGroovyMethods.*"
    )
  }

  override fun getParameterHints(element: PsiElement) = (element as? GrCall)?.doGetParameterHints() ?: emptyList()

  private fun GrCall.doGetParameterHints(): List<InlayInfo>? {
    val signature = GrClosureSignatureUtil.createSignature(this) ?: return null
    val infos = GrClosureSignatureUtil.mapParametersToArguments(signature, this) ?: return null
    val original = signature.parameters.zip(infos)

    // leave only parameters with names
    val map = original.mapNotNull {
      it.first.name?.let { name -> name to it.second }
    }.toMap()

    // leave only regular arguments and varargs
    val nonNamedArguments = map.filterValues {
      !it.isMultiArg || it.args.none { it is GrNamedArgument }
    }

    return nonNamedArguments.mapNotNull {
      val (name, info) = it
      info.args.firstOrNull()?.let { arg ->
        val inlayText = if (info.isMultiArg) "...$name" else name
        InlayInfo(inlayText, arg.textRange.startOffset)
      }
    }
  }

  override fun getMethodInfo(element: PsiElement): MethodInfo? {
    val call = element as? GrCall
    val resolved = call?.resolveMethod()
    val method = (resolved as? GrGdkMethod)?.staticMethod ?: resolved
    return method?.getMethodInfo()
  }

  private fun PsiMethod.getMethodInfo(): MethodInfo? {
    val clazzName = containingClass?.qualifiedName ?: return null
    val fullMethodName = StringUtil.getQualifiedName(clazzName, name)
    val paramNames: List<String> = parameterList.parameters.map { it.name ?: "" }
    return MethodInfo(fullMethodName, paramNames)
  }

  override val defaultBlackList: Set<String> get() = blackList

  override fun getBlackListDependencyLanguage() = JavaLanguage.INSTANCE
}