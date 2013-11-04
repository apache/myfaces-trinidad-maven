/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
/* Generated By:JJTree&JavaCC: Do not edit this line. JSParser20Constants.java */
package org.apache.myfaces.trinidadbuild.plugin.javascript.javascript20parser;

public interface JSParser20Constants {

  int EOF = 0;
  int EOL = 1;
  int WS = 2;
  int SINGLE_LINE_COMMENT = 5;
  int MULTI_LINE_COMMENT = 6;
  int AS = 8;
  int BREAK = 9;
  int CASE = 10;
  int CATCH = 11;
  int CLASS = 12;
  int CONST = 13;
  int CONTINUE = 14;
  int _DEFAULT = 15;
  int DELETE = 16;
  int DO = 17;
  int ELSE = 18;
  int EXTENDS = 19;
  int FALSE = 20;
  int FINALLY = 21;
  int FOR = 22;
  int FUNCTION = 23;
  int FUNCTION_ = 24;
  int GET = 25;
  int IF = 26;
  int IN = 27;
  int INCLUDE = 28;
  int INSTANCEOF = 29;
  int IS = 30;
  int NAMESPACE = 31;
  int NEW = 32;
  int NULL = 33;
  int PACKAGE = 34;
  int PRIVATE = 35;
  int PUBLIC = 36;
  int RETURN = 37;
  int SET = 38;
  int SUPER = 39;
  int SWITCH = 40;
  int THIS = 41;
  int THROW = 42;
  int TRUE = 43;
  int TRY = 44;
  int TYPEOF = 45;
  int USE = 46;
  int VAR = 47;
  int VOID = 48;
  int WHILE = 49;
  int WITH = 50;
  int ABSTRACT = 51;
  int DEBUGGER = 52;
  int ENUM = 53;
  int EXPORT = 54;
  int GOTO = 55;
  int IMPLEMENTS = 56;
  int INTERFACE = 57;
  int NATIVE = 58;
  int PROTECTED = 59;
  int SYNCHRONIZED = 60;
  int THROWS = 61;
  int TRANSIENT = 62;
  int VOLATILE = 63;
  int IMPORT = 64;
  int DECIMAL_LITERAL = 65;
  int HEX_LITERAL = 66;
  int OCTAL_LITERAL = 67;
  int FLOATING_POINT_LITERAL = 68;
  int EXPONENT = 69;
  int STRING_LITERAL = 70;
  int ESCAPE_SEQUENCE = 71;
  int UNTERMINATED_STRING_LITERAL = 72;
  int REGX_START_CHAR = 73;
  int REGX_BODY_CHAR_EXCLUSION = 74;
  int REGX_BODY_CHAR = 75;
  int REGEX_END_CHAR = 76;
  int REGULAR_EXPRESSION = 77;
  int IDENTIFIER = 78;
  int LETTER = 79;
  int DIGIT = 80;
  int LPAREN = 81;
  int RPAREN = 82;
  int LBRACE = 83;
  int RBRACE = 84;
  int LBRACKET = 85;
  int RBRACKET = 86;
  int SEMICOLON = 87;
  int COMMA = 88;
  int DOT = 89;
  int QUALIFIER = 90;
  int ELIPSE = 91;
  int ASSIGN = 92;
  int GT = 93;
  int LT = 94;
  int BANG = 95;
  int TILDE = 96;
  int HOOK = 97;
  int COLON = 98;
  int EQ = 99;
  int LE = 100;
  int GE = 101;
  int NE = 102;
  int SC_OR = 103;
  int SC_AND = 104;
  int SC_XOR = 105;
  int INCR = 106;
  int DECR = 107;
  int PLUS = 108;
  int MINUS = 109;
  int STAR = 110;
  int SLASH = 111;
  int BIT_AND = 112;
  int BIT_OR = 113;
  int XOR = 114;
  int REM = 115;
  int LSHIFT = 116;
  int RSIGNEDSHIFT = 117;
  int RUNSIGNEDSHIFT = 118;
  int PLUSASSIGN = 119;
  int MINUSASSIGN = 120;
  int STARASSIGN = 121;
  int SLASHASSIGN = 122;
  int ANDASSIGN = 123;
  int ORASSIGN = 124;
  int XORASSIGN = 125;
  int REMASSIGN = 126;
  int LSHIFTASSIGN = 127;
  int RSIGNEDSHIFTASSIGN = 128;
  int RUNSIGNEDSHIFTASSIGN = 129;
  int SC_ORASSIGN = 130;
  int SC_ANDASSIGN = 131;
  int SC_XORASSIGN = 132;
  int IDENTITYOPER = 133;
  int NOTIDENTITYOPER = 134;

  int DEFAULT = 0;
  int IN_SINGLE_LINE_COMMENT = 1;
  int IN_MULTI_LINE_COMMENT = 2;

  String[] tokenImage = {
    "<EOF>",
    "<EOL>",
    "<WS>",
    "\"//\"",
    "\"/*\"",
    "<SINGLE_LINE_COMMENT>",
    "\"*/\"",
    "<token of kind 7>",
    "\"as\"",
    "\"break\"",
    "\"case\"",
    "\"catch\"",
    "\"class\"",
    "\"const\"",
    "\"continue\"",
    "\"default\"",
    "\"delete\"",
    "\"do\"",
    "\"else\"",
    "\"extends\"",
    "\"false\"",
    "\"finally\"",
    "\"for\"",
    "\"function\"",
    "\"Function\"",
    "\"get\"",
    "\"if\"",
    "\"in\"",
    "\"include\"",
    "\"instanceof\"",
    "\"is\"",
    "\"namespace\"",
    "\"new\"",
    "\"null\"",
    "\"package\"",
    "\"private\"",
    "\"public\"",
    "\"return\"",
    "\"set\"",
    "\"super\"",
    "\"switch\"",
    "\"this\"",
    "\"throw\"",
    "\"true\"",
    "\"try\"",
    "\"typeof\"",
    "\"use\"",
    "\"var\"",
    "\"void\"",
    "\"while\"",
    "\"with\"",
    "\"abstract\"",
    "\"debugger\"",
    "\"enum\"",
    "\"export\"",
    "\"goto\"",
    "\"implements\"",
    "\"interface\"",
    "\"native\"",
    "\"protected\"",
    "\"synchronized\"",
    "\"throws\"",
    "\"transient\"",
    "\"volatile\"",
    "\"import\"",
    "<DECIMAL_LITERAL>",
    "<HEX_LITERAL>",
    "<OCTAL_LITERAL>",
    "<FLOATING_POINT_LITERAL>",
    "<EXPONENT>",
    "<STRING_LITERAL>",
    "<ESCAPE_SEQUENCE>",
    "<UNTERMINATED_STRING_LITERAL>",
    "<REGX_START_CHAR>",
    "<REGX_BODY_CHAR_EXCLUSION>",
    "<REGX_BODY_CHAR>",
    "<REGEX_END_CHAR>",
    "<REGULAR_EXPRESSION>",
    "<IDENTIFIER>",
    "<LETTER>",
    "<DIGIT>",
    "\"(\"",
    "\")\"",
    "\"{\"",
    "\"}\"",
    "\"[\"",
    "\"]\"",
    "\";\"",
    "\",\"",
    "\".\"",
    "\"::\"",
    "\"...\"",
    "\"=\"",
    "\">\"",
    "\"<\"",
    "\"!\"",
    "\"~\"",
    "\"?\"",
    "\":\"",
    "\"==\"",
    "\"<=\"",
    "\">=\"",
    "\"!=\"",
    "\"||\"",
    "\"&&\"",
    "\"^^\"",
    "\"++\"",
    "\"--\"",
    "\"+\"",
    "\"-\"",
    "\"*\"",
    "\"/\"",
    "\"&\"",
    "\"|\"",
    "\"^\"",
    "\"%\"",
    "\"<<\"",
    "\">>\"",
    "\">>>\"",
    "\"+=\"",
    "\"-=\"",
    "\"*=\"",
    "\"/=\"",
    "\"&=\"",
    "\"|=\"",
    "\"^=\"",
    "\"%=\"",
    "\"<<=\"",
    "\">>=\"",
    "\">>>=\"",
    "\"||=\"",
    "\"&&=\"",
    "\"^^=\"",
    "\"===\"",
    "\"!==\"",
  };

}