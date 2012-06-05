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
                xmlns:bridge="http://www.apache.org/myfaces/xml/ns/bridge/bridge-extension"
                exclude-result-prefixes="xsl xs javaee mfp fmd"
                version="1.0">

  <xsl:output method="xml" indent="yes"/>
  <xsl:param name="packageContains" />
  <xsl:param name="converterPackageContains" />
  <xsl:param name="validatorPackageContains" />
  <xsl:param name="typePrefix" />
  <xsl:param name="removeRenderers" />
  <xsl:param name="metadataComplete" />



  <xsl:key name="component-type"
           match="javaee:component"
           use="javaee:component-type/text()" />

  <xsl:key name="render-kit-id"
           match="javaee:render-kit"
           use="javaee:render-kit-id/text()" />

  <!-- switch off default text processing -->
  <xsl:template match="//text()" />

  <!-- these are used for inserting a namespace declaration in xslt 1.0 -->
  <xsl:variable name="tr">
    <xsl:element name="tr:xxx" namespace="http://myfaces.apache.org/trinidad"/>
  </xsl:variable>
  <xsl:variable name="trh">
    <xsl:element name="trh:xxx" namespace="http://myfaces.apache.org/trinidad/html"/>
  </xsl:variable>
  <xsl:variable name="fmd">
    <xsl:element name="fmd:xxx" namespace="http://java.sun.com/xml/ns/javaee/faces/design-time-metadata"/>
  </xsl:variable>
  <xsl:variable name="mfp">
    <xsl:element name="mfp:xxx" namespace="http://myfaces.apache.org/maven-faces-plugin"/>
  </xsl:variable>
  <xsl:variable name="mafp">
    <xsl:element name="mafp:xxx" namespace="http://xmlns.oracle.com/maven-adf-faces-plugin"/>
  </xsl:variable>
  <xsl:variable name="bridge">
    <xsl:element name="bridge:xxx" namespace="http://www.apache.org/myfaces/xml/ns/bridge/bridge-extension"/>
  </xsl:variable>

  <xsl:template match="/javaee:faces-config" >
    <xsl:element name="faces-config"
                 namespace="http://java.sun.com/xml/ns/javaee" >
      <!-- Add namespace declarations at root element, so they don't show up at lower elements when we change namespaces -->
      <xsl:copy-of select="exsl:node-set($tr)//namespace::*"/>
      <xsl:copy-of select="exsl:node-set($trh)//namespace::*"/>
      <xsl:copy-of select="exsl:node-set($fmd)//namespace::*"/>
      <xsl:copy-of select="exsl:node-set($mfp)//namespace::*"/>
      <xsl:copy-of select="exsl:node-set($mafp)//namespace::*"/>
      <xsl:copy-of select="exsl:node-set($bridge)//namespace::*"/>
      <xsl:attribute name="xsi:schemaLocation">http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-facesconfig_2_0.xsd</xsl:attribute>
      <xsl:if test="$metadataComplete">
        <xsl:attribute name="metadata-complete">true</xsl:attribute>
      </xsl:if>

      <xsl:attribute name="version">2.0</xsl:attribute>
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

  <!-- this templates applies javaee:property templates
       for a component and all supertypes -->
  <xsl:template name="apply-property-templates" >
    <xsl:param name="component" />
    <xsl:param name="skip"></xsl:param>
    <xsl:variable name="componentSupertype"
      select="$component/javaee:component-extension/mfp:component-supertype/text()" />
    <xsl:if test="$componentSupertype" >
      <xsl:call-template name="apply-property-templates" >
        <xsl:with-param name="component"
          select="key('component-type', $componentSupertype)" />
        <!-- "Recursively" build a skip set of nodes. This is created as a
          string and appended to for every super class applied. The square
          brackets are used for a "whole-world" type of functionality. -->
        <xsl:with-param name="skip"><xsl:value-of select="$skip"
          /><xsl:for-each
            select="$component/javaee:property/javaee:property-name"
            >[<xsl:value-of select="normalize-space(text())" />]</xsl:for-each>
        </xsl:with-param>
      </xsl:call-template>
    </xsl:if>
    <!-- uncomment this code to help debug the skip functionality: -->
    <!--xsl:comment>
      Skip is: <xsl:value-of select="$skip" />
    </xsl:comment-->
    <xsl:for-each select="$component/javaee:property">
      <xsl:variable name="searchFor">[<xsl:value-of
        select="normalize-space(javaee:property-name/text())"/>]</xsl:variable>
      <!-- Do not include this element if it is overridden in the sub-type -->
      <xsl:if
        test="not(contains($skip, $searchFor))">
        <xsl:apply-templates select="." />
      </xsl:if>
    </xsl:for-each>
  </xsl:template>

  <!-- this templates applies javaee:attribute templates
       for a component and all supertypes -->
  <xsl:template name="apply-attribute-templates" >
    <xsl:param name="component" />
    <xsl:param name="skip"></xsl:param>
    <xsl:variable name="componentSupertype"
                  select="$component/javaee:component-extension/mfp:component-supertype/text()" />
    <xsl:if test="$componentSupertype" >
      <xsl:call-template name="apply-attribute-templates" >
        <xsl:with-param name="component"
                        select="key('component-type', $componentSupertype)" />
        <!-- "Recursively" build a skip set of nodes. This is created as a
          string and appended to for every super class applied. The square
          brackets are used for a "whole-world" type of functionality. -->
        <xsl:with-param name="skip"><xsl:value-of select="$skip"
          /><xsl:for-each
            select="$component/javaee:attribute/javaee:attribute-name"
            >[<xsl:value-of select="normalize-space(text())" />]</xsl:for-each>
        </xsl:with-param>
      </xsl:call-template>
    </xsl:if>
    <!-- uncomment this code to help debug the skip functionality: -->
    <!--xsl:comment>
      Skip is: <xsl:value-of select="$skip" />
    </xsl:comment-->
    <xsl:for-each select="$component/javaee:attribute">
      <xsl:variable name="searchFor">[<xsl:value-of
        select="normalize-space(javaee:attribute-name/text())"/>]</xsl:variable>
      <!-- Do not include this element if it is overridden in the sub-type -->
      <xsl:if
        test="not(contains($skip, $searchFor))">
        <xsl:apply-templates select="." />
      </xsl:if>
    </xsl:for-each>
  </xsl:template>

  <!-- this templates applies javaee:facet templates
       for a component and all supertypes -->
  <xsl:template name="apply-facet-templates" >
    <xsl:param name="component" />
    <xsl:param name="skip"></xsl:param>
    <xsl:variable name="componentSupertype"
                  select="$component/javaee:component-extension/mfp:component-supertype/text()" />
    <xsl:if test="$componentSupertype" >
      <xsl:call-template name="apply-facet-templates" >
        <xsl:with-param name="component"
                        select="key('component-type', $componentSupertype)" />
        <!-- "Recursively" build a skip set of nodes. This is created as a
          string and appended to for every super class applied. The square
          brackets are used for a "whole-world" type of functionality. -->
        <xsl:with-param name="skip"><xsl:value-of select="$skip"
          /><xsl:for-each
            select="$component/javaee:facet/javaee:facet-name"
            >[<xsl:value-of select="normalize-space(text())" />]</xsl:for-each>
        </xsl:with-param>
      </xsl:call-template>
    </xsl:if>
    <!-- uncomment this code to help debug the skip functionality: -->
    <!--xsl:comment>
      Skip is: <xsl:value-of select="$skip" />
    </xsl:comment-->
    <xsl:for-each select="$component/javaee:facet">
      <xsl:variable name="searchFor">[<xsl:value-of
        select="normalize-space(javaee:facet-name/text())"/>]</xsl:variable>
      <!-- Do not include this element if it is overridden in the sub-type -->
      <xsl:if
        test="not(contains($skip, $searchFor))">
        <xsl:apply-templates select="." />
      </xsl:if>
    </xsl:for-each>
  </xsl:template>

  <xsl:template match="//javaee:component[javaee:component-extension/mfp:component-supertype]"
                priority="1" >
    <xsl:element name="component" >
      <xsl:apply-templates select="javaee:description" />
      <xsl:apply-templates select="javaee:display-name" />
      <xsl:apply-templates select="javaee:icon" />
      <xsl:apply-templates select="javaee:component-type" />
      <xsl:apply-templates select="javaee:component-class" />
      <xsl:call-template name="apply-facet-templates" >
        <xsl:with-param name="component" select="." />
      </xsl:call-template>
      <xsl:call-template name="apply-attribute-templates" >
        <xsl:with-param name="component" select="." />
      </xsl:call-template>
      <xsl:call-template name="apply-property-templates" >
        <xsl:with-param name="component" select="." />
      </xsl:call-template>
      <xsl:apply-templates select="javaee:component-extension" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:name" >
    <xsl:element name="name" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>
  
  <xsl:template match="//javaee:ordering" >
    <xsl:element name="ordering" >
      <xsl:apply-templates select="javaee:after" />
      <xsl:apply-templates select="javaee:before" />
    </xsl:element>
  </xsl:template>
  
  <xsl:template match="//javaee:after" >
    <xsl:element name="after" >
      <xsl:apply-templates />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:before" >
    <xsl:element name="before" >
      <xsl:apply-templates />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:absolute-ordering" >
    <xsl:element name="absolute-ordering" >
      <xsl:apply-templates />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:behavior" >
    <xsl:element name="behavior" >
      <xsl:apply-templates select="javaee:behavior-class[contains(text(), $packageContains)]" />
      <xsl:apply-templates select="javaee:behavior-extension" />
      <xsl:apply-templates select="javaee:behavior-id[contains(text(), $packageContains)]" />
      <xsl:apply-templates select="javaee:description" />
      <xsl:apply-templates select="javaee:display-name" />
      <xsl:apply-templates select="javaee:icon" />
      <xsl:apply-templates select="javaee:attribute" />
      <xsl:apply-templates select="javaee:property" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:behavior-class" >
    <xsl:element name="behavior-class" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:behavior-extension" >
    <xsl:element name="behavior-extension" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:behavior-id" >
    <xsl:element name="behavior-id" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:application" >
    <xsl:element name="application" >
      <xsl:apply-templates select="javaee:action-listener[contains(text(), $packageContains)]" />
      <xsl:apply-templates select="javaee:default-render-kit-id" />
      <xsl:apply-templates select="javaee:message-bundle[contains(text(), $packageContains)]" />
      <xsl:apply-templates select="javaee:view-handler[contains(text(), $packageContains)]" />
      <xsl:apply-templates select="javaee:state-manager[contains(text(), $packageContains)]" />
      <xsl:apply-templates select="javaee:navigation-handler[contains(text(), $packageContains)]" />
      <xsl:apply-templates select="javaee:el-resolver[contains(text(), $packageContains)]" />
      <xsl:apply-templates select="javaee:property-resolver[contains(text(), $packageContains)]" />
      <xsl:apply-templates select="javaee:variable-resolver[contains(text(), $packageContains)]" />
      <xsl:apply-templates select="javaee:partial-traversal[contains(text(), $packageContains)]" />
      <xsl:apply-templates select="javaee:system-event-listener" />
      <xsl:apply-templates select="javaee:default-validators" />
      <xsl:apply-templates select="javaee:locale-config" />
      <xsl:apply-templates select="javaee:resource-bundle" />
      <xsl:apply-templates select="javaee:application-extension" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:action-listener" >
    <xsl:element name="action-listener" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:default-render-kit-id" >
    <xsl:element name="default-render-kit-id" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:message-bundle" >
    <xsl:element name="message-bundle" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:navigation-handler" >
    <xsl:element name="navigation-handler" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:view-handler" >
    <xsl:element name="view-handler" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:state-manager" >
    <xsl:element name="state-manager" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:el-resolver" >
    <xsl:element name="el-resolver" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:property-resolver" >
    <xsl:element name="property-resolver" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:variable-resolver" >
    <xsl:element name="variable-resolver" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>
  
  <xsl:template match="//javaee:partial-traversal" >
    <xsl:element name="partial-traversal" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:system-event-listener" >
    <xsl:element name="system-event-listener" >
      <xsl:apply-templates select="javaee:system-event-class" />
      <xsl:apply-templates select="javaee:system-event-listener-class[contains(text(), $packageContains)]" />
      <xsl:apply-templates select="javaee:source-class" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:system-event-class" >
    <xsl:element name="system-event-class" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:system-event-listener-class" >
    <xsl:element name="system-event-listener-class" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:source-class" >
    <xsl:element name="source-class" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:default-validators" >
    <xsl:element name="default-validators" >
      <xsl:apply-templates select="javaee:validator-id"/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:locale-config" >
    <xsl:element name="locale-config" >
      <xsl:apply-templates />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:factory" >
    <xsl:element name="factory" >
      <xsl:apply-templates select="javaee:application-factory[contains(text(), $packageContains)]" />
      <xsl:apply-templates select="javaee:faces-context-factory[contains(text(), $packageContains)]" />
      <xsl:apply-templates select="javaee:lifecycle-factory[contains(text(), $packageContains)]" />
      <xsl:apply-templates select="javaee:partial-view-context-factory[contains(text(), $packageContains)]" />
      <xsl:apply-templates select="javaee:exception-handler-factory[contains(text(), $packageContains)]" />
      <xsl:apply-templates select="javaee:render-kit-factory[contains(text(), $packageContains)]" />
      <xsl:apply-templates select="javaee:view-declaration-language-factory[contains(text(), $packageContains)]" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:application-factory" >
    <xsl:element name="application-factory" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:faces-context-factory" >
    <xsl:element name="faces-context-factory" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:lifecycle-factory" >
    <xsl:element name="lifecycle-factory" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:partial-view-context-factory" >
    <xsl:element name="partial-view-context-factory" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>  

  <xsl:template match="//javaee:exception-handler-factory" >
    <xsl:element name="exception-handler-factory" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>  

  <xsl:template match="//javaee:render-kit-factory" >
    <xsl:element name="render-kit-factory" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>
  
  <xsl:template match="//javaee:view-declaration-language-factory" >
    <xsl:element name="view-declaration-language-factory" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:component" >
    <xsl:element name="component" >
      <xsl:apply-templates />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:component-type" >
    <xsl:element name="component-type" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:component-class" >
    <xsl:element name="component-class" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:component/javaee:facet[1]" priority="1" >
    <xsl:comment><xsl:value-of select="parent::node()/javaee:component-type/text()" /> facets</xsl:comment>
    <xsl:element name="facet" >
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:facet" >
    <xsl:element name="facet" >
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:facet-name" >
    <xsl:element name="facet-name" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:facet-extension">
    <!-- Make sure not empty -->
    <xsl:if test="*">
      <xsl:element name="facet-extension">
        <!-- Check for possible children of the metadata -->
        <xsl:if test="*[namespace-uri() != 'http://java.sun.com/xml/ns/javaee']">
          <xsl:apply-templates select="fmd:facet-metadata"/>
          <xsl:element name="facet-metadata">
            <!-- Select metadata children -->
            <xsl:apply-templates select="mfp:facet-metadata/*[
              namespace-uri() != 'http://java.sun.com/xml/ns/javaee']" />
            <!-- Add non-metadata children under the metadata -->
            <xsl:apply-templates select="*[namespace-uri() != 'http://java.sun.com/xml/ns/javaee'
              and namespace-uri() != 'http://java.sun.com/xml/ns/javaee/faces/design-time-metadata'
              and (
                namespace-uri() != 'http://myfaces.apache.org/maven-faces-plugin'
                or name() != 'mfp:facet-metadata'
                )]" />
          </xsl:element>
        </xsl:if>
      </xsl:element>
    </xsl:if>
  </xsl:template>


  <xsl:template match="//javaee:component/javaee:attribute[1]" priority="1" >
    <xsl:comment><xsl:value-of select="parent::node()/javaee:component-type/text()" /> attributes</xsl:comment>
    <xsl:element name="attribute" >
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:component/javaee:attribute[1]" priority="1" >
    <xsl:comment><xsl:value-of select="parent::node()/javaee:component-type/text()" /> attributes</xsl:comment>
    <xsl:element name="attribute" >
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:attribute" >
    <xsl:element name="attribute" >
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:attribute-name" >
    <xsl:element name="attribute-name" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:attribute-class" >
    <xsl:element name="attribute-class" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:attribute-extension" >
    <xsl:element name="attribute-extension" >
      <xsl:apply-templates />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:component/javaee:property[1]" priority="1" >
    <xsl:comment><xsl:value-of select="parent::node()/javaee:component-type/text()" /> properties</xsl:comment>
    <xsl:element name="property" >
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:property[javaee:property-name/text() = 'binding']" priority='2' >
    <!-- skip over properties named 'binding' -->
  </xsl:template>

  <xsl:template match="//javaee:property" >
    <xsl:element name="property" >
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:property-name" >
    <xsl:element name="property-name" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:property-class" >
    <xsl:element name="property-class" >
      <!-- eliminate generics for 1.4-based classes in JSF 1.1 -->
      <xsl:choose>
        <xsl:when test="contains(text(), '&lt;')" >
          <xsl:value-of select="substring-before(text(), '&lt;')" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="text()" />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:property-extension">
    <!-- Make sure not empty -->
    <xsl:if test="*">
      <xsl:element name="property-extension">
        <!-- Check for possible children of the metadata -->
        <xsl:if test="*[namespace-uri() != 'http://java.sun.com/xml/ns/javaee']">
          <xsl:apply-templates select="fmd:property-metadata"/>
          <xsl:element name="property-metadata">
            <!-- Select metadata children -->
            <xsl:apply-templates select="mfp:property-metadata/*[
              namespace-uri() != 'http://java.sun.com/xml/ns/javaee']" />
            <!-- Add non-metadata children under the metadata -->
            <xsl:apply-templates select="*[namespace-uri() != 'http://java.sun.com/xml/ns/javaee'
              and namespace-uri() != 'http://java.sun.com/xml/ns/javaee/faces/design-time-metadata'
              and (
                namespace-uri() != 'http://myfaces.apache.org/maven-faces-plugin'
                or name() != 'mfp:property-metadata'
                )]" />
          </xsl:element>
        </xsl:if>
      </xsl:element>
    </xsl:if>
  </xsl:template>

  <!-- this templates grabs the component-family from an ancestor -->
  <xsl:template match="//javaee:component-extension[mfp:component-supertype]" priority="1" >
    <xsl:variable name="componentSupertype"
                  select="mfp:component-supertype/text()" />
    <xsl:element name="component-extension" >
      <xsl:element name="component-family">
        <xsl:value-of select="key('component-type', $componentSupertype)/javaee:component-extension/mfp:component-family/text()" />
      </xsl:element>
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:component-extension[mfp:component-family]" priority="2" >
    <xsl:element name="component-extension" >
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:component-extension" >
    <xsl:comment>Warning: this component has no component-family!</xsl:comment>
    <xsl:element name="component-extension" >
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//mfp:component-metadata" >
    <xsl:element name="component-metadata" >
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:converter" >
    <xsl:element name="converter" >
      <!-- xsl:apply-templates/ TODO use this instead -->
      <xsl:apply-templates select="javaee:description"/>
      <xsl:apply-templates select="javaee:display-name"/>
      <xsl:apply-templates select="javaee:converter-id"/>
      <xsl:apply-templates select="javaee:converter-for-class"/>
      <xsl:apply-templates select="javaee:converter-class"/>
      <xsl:apply-templates select="javaee:property"/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:converter-id" >
    <xsl:element name="converter-id" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:converter-for-class" >
    <xsl:element name="converter-for-class" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:converter-class" >
    <xsl:element name="converter-class" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:managed-bean" >
    <xsl:element name="managed-bean" >
      <xsl:apply-templates />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:managed-bean-name" >
    <xsl:element name="managed-bean-name" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:managed-bean-class" >
    <xsl:element name="managed-bean-class" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:managed-bean-scope" >
    <xsl:element name="managed-bean-scope" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:managed-property" >
    <xsl:element name="managed-property" >
      <xsl:apply-templates />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:null-value" >
    <xsl:element name="null-value" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:value" >
    <xsl:element name="value" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:map-entries" >
    <xsl:element name="map-entries" >
      <xsl:apply-templates />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:map-entry" >
    <xsl:element name="map-entry" >
      <xsl:apply-templates />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:key" >
    <xsl:element name="key" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:key-class" >
    <xsl:element name="key-class" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:value-class" >
    <xsl:element name="value-class" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:message-bundle" >
    <xsl:element name="message-bundle" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:list-entries" >
    <xsl:element name="list-entries" >
      <xsl:apply-templates />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:navigation-rule" >
    <xsl:element name="navigation-rule" >
      <xsl:apply-templates />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:from-view-id" >
    <xsl:element name="from-view-id" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:navigation-case" >
    <xsl:element name="navigation-case" >
      <xsl:apply-templates />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:from-action" >
    <xsl:element name="from-action" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:from-outcome" >
    <xsl:element name="from-outcome" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:to-view-id" >
    <xsl:element name="to-view-id" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:redirect" >
    <xsl:element name="redirect" />
  </xsl:template>
  
  <xsl:template match="//javaee:name" >
    <xsl:element name="name" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>
  
  <xsl:template match="//javaee:others" >
    <xsl:element name="others" />
  </xsl:template>
  

  <xsl:template match="//javaee:referenced-bean" >
    <xsl:element name="referenced-bean" >
      <xsl:apply-templates />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:referenced-bean-name" >
    <xsl:element name="referenced-bean-name" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:referenced-bean-class" >
    <xsl:element name="referenced-bean-class" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>
  
  <xsl:template match="//javaee:application-extension">
    <xsl:element name="application-extension">
      <xsl:copy-of select="*"/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:render-kit" >
    <xsl:element name="render-kit" >
      <xsl:apply-templates />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:render-kit-id" >
    <xsl:element name="render-kit-id" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:render-kit-class" >
    <xsl:element name="render-kit-class" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

<!-- TODO: flatten component properties into renderer as attributes -->
<!--
  <xsl:template match="//javaee:renderer[javaee:renderer-extension/mfp:component-type]"
                priority="1" >
    <xsl:element name="renderer" >
      <xsl:apply-templates select="javaee:component-family" />
      <xsl:apply-templates select="javaee:renderer-type" />
      <xsl:apply-templates select="javaee:renderer-class" />
      <xsl:variable name="componentType" select="javaee:renderer-extension/mfp:component-type/text()" />
      <xsl:for-each select="key('component-type', $componentType)/javaee:property" >
        <xsl:element name="attribute" >
          <xsl:apply-templates select="javaee:description" />
          <xsl:apply-templates select="javaee:display-name" />
          <xsl:apply-templates select="javaee:icon" />
          <xsl:element name="attribute-name" >
            <xsl:value-of select="javaee:property-name" />
          </xsl:element>
          <xsl:element name="attribute-class" >
            <xsl:value-of select="javaee:property-class" />
          </xsl:element>
          <xsl:apply-templates select="javaee:default-value" />
          <xsl:apply-templates select="javaee:suggested-value" />
          <xsl:element name="attribute-extension" >
            <xsl:apply-templates select="mfp:property-extension/*" />
          </xsl:element>
        </xsl:element>
      </xsl:for-each>
      <xsl:apply-templates select="javaee:attribute" />
      <xsl:apply-templates select="javaee:renderer-extension" />
    </xsl:element>
  </xsl:template>
-->

  <xsl:template match="//javaee:renderer" >
    <xsl:element name="renderer" >
      <xsl:apply-templates />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:component-family" >
    <xsl:element name="component-family" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:renderer-type" >
    <xsl:element name="renderer-type" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:renderer-class" >
    <xsl:element name="renderer-class" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:client-behavior-renderer" >
    <xsl:element name="client-behavior-renderer" >
      <xsl:apply-templates />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:client-behavior-renderer-type" >
    <xsl:element name="client-behavior-renderer-type" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:client-behavior-renderer-class" >
    <xsl:element name="client-behavior-renderer-class" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:renderer-extension[mfp:unsupported-agents]" >
    <xsl:element name="renderer-extension" >
      <xsl:element name="renderer-metadata" >
        <xsl:apply-templates/>
      </xsl:element>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:lifecycle" >
    <xsl:element name="lifecycle" >
      <xsl:apply-templates select="javaee:phase-listener[contains(text(), $packageContains)]" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:phase-listener" >
    <xsl:element name="phase-listener" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:default-locale" >
    <xsl:element name="default-locale" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:supported-locale" >
    <xsl:element name="supported-locale" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:validator" >
    <xsl:element name="validator" >
      <!-- xsl:apply-templates/ TODO use this instead -->
      <xsl:apply-templates select="javaee:display-name"/>
      <xsl:apply-templates select="javaee:validator-id"/>
      <xsl:apply-templates select="javaee:validator-class"/>
      <xsl:apply-templates select="javaee:property"/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:validator-id" >
    <xsl:element name="validator-id" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:validator-class" >
    <xsl:element name="validator-class" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:description" >
    <xsl:element name="description" >
      <xsl:apply-templates select="@*" />
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template
    match="//javaee:property[javaee:property-extension/mfp:long-description]/javaee:description"
    priority="1">
    <xsl:element name="description" >
      <xsl:apply-templates select="../javaee:property-extension/mfp:long-description/@*" />
      <xsl:value-of select="../javaee:property-extension/mfp:long-description/text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:description/@xml:lang" >
    <xsl:attribute name="xml:lang" ><xsl:value-of select="@xml:lang" /></xsl:attribute>
  </xsl:template>

  <xsl:template match="//javaee:display-name" >
    <xsl:element name="display-name" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:icon" >
    <xsl:element name="icon" >
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:small-icon" >
    <xsl:element name="small-icon" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:large-icon" >
    <xsl:element name="large-icon" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:default-value" >
    <xsl:element name="default-value" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:suggested-value" >
    <xsl:element name="suggested-value" >
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <xsl:template match="//mfp:property-values">
    <!-- Rename this element -->
    <xsl:element name="attribute-values">
      <xsl:value-of select="text()" />
    </xsl:element>
  </xsl:template>

  <!-- Handle metadata we do not know about by letting it through.  Currently,
   just for property-extension and component-metadata, but should be global.
   See JIRA issues ADFFACES-358, ADFFACES-361 and ADFFACES-472 -->
  <xsl:template match="javaee:property-extension/*[
    namespace-uri() != 'http://java.sun.com/xml/ns/javaee'
    and namespace-uri() !='http://myfaces.apache.org/maven-faces-plugin'
    and namespace-uri() !='http://java.sun.com/xml/ns/javaee/faces/design-time-metadata']">
    <xsl:copy-of select="."/>
  </xsl:template>

  <xsl:template match="mfp:component-metadata/*[
    namespace-uri() != 'http://java.sun.com/xml/ns/javaee'
    and namespace-uri() !='http://myfaces.apache.org/maven-faces-plugin']">
    <xsl:copy-of select="."/>
  </xsl:template>

  <xsl:template match="fmd:global-metadata/*[
    namespace-uri() != 'http://java.sun.com/xml/ns/javaee'
    and namespace-uri() !='http://myfaces.apache.org/maven-faces-plugin'
    and namespace-uri() !='http://java.sun.com/xml/ns/javaee/faces/design-time-metadata']">
    <xsl:copy-of select="."/>
  </xsl:template>

  <xsl:template match="//*[
    namespace-uri() = 'http://myfaces.apache.org/maven-faces-plugin']"
    priority="-1">
    <xsl:element name="{local-name()}" >
      <xsl:apply-templates select="@*|node()"/>
      <xsl:value-of select="text()"/>
    </xsl:element>
  </xsl:template>

  <!-- Rule for the jsr-276 (top level) component metadata, just copy the whole thing -->
  <xsl:template match="//fmd:component-metadata">
    <xsl:element name="fmd:component-metadata">
     <xsl:copy-of select="*"/>
    </xsl:element>
  </xsl:template>

  <!-- Rule for the jsr-276 (top level) property metadata, just copy the whole thing -->
  <xsl:template match="//fmd:property-metadata">
    <xsl:element name="fmd:property-metadata">
     <xsl:copy-of select="*"/>
    </xsl:element>
  </xsl:template>

  <!-- Rule for the jsr-276 (top level) facet metadata, just copy the whole thing -->
  <xsl:template match="//fmd:facet-metadata">
    <xsl:element name="fmd:facet-metadata">
     <xsl:copy-of select="*"/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//javaee:faces-config-extension">
    <xsl:apply-templates select="fmd:global-metadata"/>
  </xsl:template>

  <!-- Rule for the jsr-276 (top level) global metadata extension -->
  <xsl:template match="//fmd:global-metadata">
    <xsl:element name="faces-config-extension">
      <xsl:element name="fmd:global-metadata">
        <xsl:apply-templates select="fmd:contract-definitions"/>
        <xsl:apply-templates select="fmd:component-category-definitions"/>
        <xsl:apply-templates select="fmd:property-category-definitions"/>
        <xsl:apply-templates select="fmd:faces-taglib-definitions"/>
        <!-- Include non-metadata children -->
        <xsl:apply-templates select="*[namespace-uri() != 'http://java.sun.com/xml/ns/javaee'
          and namespace-uri() != 'http://java.sun.com/xml/ns/javaee/faces/design-time-metadata'
          and namespace-uri() != 'http://myfaces.apache.org/maven-faces-plugin']"/>
      </xsl:element>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//fmd:contract-definitions">
    <xsl:element name="fmd:contract-definitions">
      <xsl:copy-of select="*"/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//fmd:component-category-definitions">
    <xsl:element name="fmd:component-category-definitions">
      <xsl:copy-of select="*"/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//fmd:property-category-definitions">
    <xsl:element name="fmd:property-category-definitions">
      <xsl:copy-of select="*"/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//fmd:faces-taglib-definitions">
    <xsl:element name="fmd:faces-taglib-definitions">
      <xsl:apply-templates select="fmd:faces-taglib"/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="//fmd:faces-taglib">
    <xsl:element name="fmd:faces-taglib">
      <xsl:copy-of select="*"/>
      <xsl:variable name="tagPrefix" select="fmd:short-name/text()"/>
      <xsl:apply-templates select="*"/>
        <xsl:for-each select="//javaee:validator">
          <xsl:if test="starts-with(javaee:validator-extension/mfp:tag-name/text(), $tagPrefix)" >
            <xsl:element name="fmd:tag">
              <xsl:element name="fmd:name">
                <xsl:value-of select="substring-after(javaee:validator-extension/mfp:tag-name/text(), ':')"/>
              </xsl:element>
              <xsl:element name="fmd:validator-id">
                <xsl:value-of select="javaee:validator-id/text()"/>
              </xsl:element>
            </xsl:element>
          </xsl:if>
        </xsl:for-each>
        <xsl:for-each select="//javaee:converter" >
          <xsl:if test="starts-with(javaee:converter-extension/mfp:tag-name/text(), $tagPrefix)" >
            <xsl:element name="fmd:tag">
              <xsl:element name="fmd:name">
                <xsl:value-of select="substring-after(javaee:converter-extension/mfp:tag-name/text(), ':')"/>
              </xsl:element>
              <xsl:element name="fmd:converter-id">
                <xsl:value-of select="javaee:converter-id/text()"/>
              </xsl:element>
            </xsl:element>
          </xsl:if>
        </xsl:for-each>
        <xsl:for-each select="//javaee:component" >
          <xsl:if test="starts-with(javaee:component-extension/mfp:tag-name/text(), $tagPrefix)" >
            <xsl:element name="fmd:tag">
              <xsl:element name="fmd:name">
                <xsl:value-of select="substring-after(javaee:component-extension/mfp:tag-name/text(), ':')"/>
              </xsl:element>
              <xsl:element name="fmd:component-type">
                <xsl:value-of select="javaee:component-type/text()"/>
              </xsl:element>
              <xsl:element name="fmd:renderer-type">
                <xsl:value-of select="javaee:component-extension/mfp:renderer-type/text()"/>
              </xsl:element>
            </xsl:element>
          </xsl:if>
        </xsl:for-each>
    </xsl:element>
  </xsl:template>

  <!-- Blacklisted mfp: that should not be copied over into the faces-config.xml: -->
  <xsl:template match="//mfp:alternate-class" />
  <xsl:template match="//mfp:author" />
  <xsl:template match="//mfp:component-metadata/mfp:group" />
  <xsl:template match="//mfp:component-superclass" />
  <xsl:template match="//mfp:component-supertype" />
  <xsl:template match="//mfp:event" />
  <xsl:template match="//mfp:example" />
  <xsl:template match="//mfp:implementation-type" />
  <xsl:template match="//mfp:java-constructor" />
  <xsl:template match="//mfp:javadoc-tags" />
  <xsl:template match="//mfp:javascript-class" />
  <xsl:template match="//mfp:jsp-property-name" />
  <xsl:template match="//mfp:long-description" />
  <xsl:template match="//mfp:method-binding-signature" />
  <xsl:template match="//mfp:screenshot" />
  <xsl:template match="//mfp:short-description" />
  <xsl:template match="//mfp:state-holder" />
  <xsl:template match="//mfp:tag-attribute-excluded" />
  <xsl:template match="//mfp:tag-class" />
  <xsl:template match="//mfp:tag-class-modifier" />
  <xsl:template match="//mfp:tag-name" />
  <xsl:template match="//mfp:uix2-local-name" />
  <xsl:template match="//mfp:unsupported-render-kit" />
  <xsl:template match="//mfp:unsupported-render-kits" />
  <xsl:template match="//mfp:use-max-time" />
  <xsl:template match="//mfp:warn-if-not-specified" />

</xsl:stylesheet>
