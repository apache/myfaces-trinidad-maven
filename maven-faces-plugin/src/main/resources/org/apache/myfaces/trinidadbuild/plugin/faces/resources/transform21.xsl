<?xml version="1.0" ?>
<!--
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.

-->
<xsl:stylesheet xmlns="http://java.sun.com/xml/ns/javaee"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xmlns:javaee="http://java.sun.com/xml/ns/javaee"
                xmlns:mfp="http://myfaces.apache.org/maven-faces-plugin"
                xmlns:fmd="http://java.sun.com/xml/ns/javaee/faces/design-time-metadata"
                xmlns:exsl="http://exslt.org/common"
                exclude-result-prefixes="xsl xs javaee mfp fmd"
                version="1.0">

  <xsl:import href="jar:resources/transform20.xsl"/>

<!-- This template is to override the JSF version and XSD to 2.1 from the transform20.xsl -->
  <xsl:template match="/javaee:faces-config" >
    <xsl:element name="faces-config"
                 namespace="http://java.sun.com/xml/ns/javaee" >
      <!-- Add namespace declarations at root element, so they don't show up at lower elements when we change namespaces -->
      <xsl:copy-of select="exsl:node-set($tr)//namespace::*"/>
      <xsl:copy-of select="exsl:node-set($trh)//namespace::*"/>
      <xsl:copy-of select="exsl:node-set($fmd)//namespace::*"/>
      <xsl:copy-of select="exsl:node-set($mfp)//namespace::*"/>
      <xsl:copy-of select="exsl:node-set($mafp)//namespace::*"/>
      <xsl:attribute name="xsi:schemaLocation">http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-facesconfig_2_1.xsd</xsl:attribute>
      <xsl:if test="$metadataComplete">
        <xsl:attribute name="metadata-complete">true</xsl:attribute>
      </xsl:if>

      <xsl:attribute name="version">2.1</xsl:attribute>
      <xsl:apply-templates select="javaee:name" />
      <xsl:apply-templates select="javaee:ordering" />
      <xsl:apply-templates select="javaee:absolute-ordering" />
      <xsl:apply-templates select="javaee:behavior" />
      <xsl:apply-templates select="javaee:application" />
      <xsl:apply-templates select="javaee:factory" />
      <xsl:apply-templates select="javaee:component[not(contains(javaee:component-extension/mfp:component-class-modifier/text(), 'abstract')) and
        (starts-with(javaee:component-type, $typePrefix) or 
          (contains(javaee:component-type, 'javax.faces.ViewRoot')))]" />
      <xsl:apply-templates select="javaee:converter[contains(javaee:converter-class, $converterPackageContains)]" />
      <xsl:apply-templates select="javaee:managed-bean[contains(javaee:managed-bean-class, $packageContains)]" />
      <xsl:apply-templates select="javaee:navigation-rule" />
      <xsl:apply-templates select="javaee:referenced-bean" />
      <!-- merge the render-kits together -->
      <xsl:for-each select="javaee:render-kit[contains(javaee:render-kit-class, $packageContains)]" >
        <xsl:element name="render-kit" >
          <xsl:apply-templates select="javaee:description" />
          <xsl:apply-templates select="javaee:display-name" />
          <xsl:apply-templates select="javaee:icon" />
          <xsl:apply-templates select="javaee:render-kit-id" />
          <xsl:apply-templates select="javaee:render-kit-class" />

          <!-- client-behavior-renderer -->
            <xsl:for-each select="key('render-kit-id', javaee:render-kit-id/text())" >
              <xsl:apply-templates select="javaee:client-behavior-renderer[contains(javaee:client-behavior-renderer-class, $packageContains)]" />
            </xsl:for-each>

          <!-- Drop renderers if desired -->
          <xsl:if test="$removeRenderers != 'true'">
            <xsl:for-each select="key('render-kit-id', javaee:render-kit-id/text())" >
              <xsl:apply-templates select="javaee:renderer[contains(javaee:renderer-class, $packageContains)]" />
            </xsl:for-each>
          </xsl:if>
        </xsl:element>
      </xsl:for-each>
      <xsl:apply-templates select="javaee:lifecycle[contains(javaee:phase-listener, $packageContains)]" />
      <xsl:apply-templates select="javaee:validator[contains(javaee:validator-class, $validatorPackageContains)]" />
      <xsl:apply-templates select="javaee:faces-config-extension" />
    </xsl:element>
  </xsl:template>

</xsl:stylesheet>
