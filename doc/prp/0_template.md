# Project Requirement Proposal (PRP)
<!-- Adapted from https://github.com/Wirasm/PRPs-agentic-eng/tree/development/PRPs -->

You are a senior software engineer.
Use the information below to implement a new feature or improvement in this software project.

## Goal

**Feature Goal**: Import a point of sale (Pos) from an OpenStreetMap node

**Deliverable**:
- Add new POST endpoint /api/pos/import/osm/{nodeId} that allows API users to import a POS based on an OpenStreetMap node.
    - Use already exisiting implementations, e.g. the skeleton of the `importFromOsmNode` method in `PosServiceImpl`
- Extend PosService interface by adding a importFromOsmNode method.
- Add example of new OSM import endpoint to README file.

**Success Definition**:
- A POST request to /api/pos/import/osm/{nodeId} accepts a nodeId, fetches the OSM XML, creates POS from XML and saves the result.
- The import creates exactly one new POS with all required fields filled.
- Suitable tests, that test common and edge cases, pass.
- Appropiate HTTP status codes are returned 

## User Persona (if applicable)

**Target User**: developer, the user does not interact directly with the API

**Use Case**: quickly import verified information on point of sale from OpenStreetMap

**User Journey**:
1. User is unable to find point of sale
2. Chooses to import with shortcut via OSM
3. Selects point of sale, for which id is fetched
4. Point of sale is imported via /api/pos/import/osm/{nodeId} endpoint.

Note that the scope is to implment the endpoint, the user journey is there to illustrate why that may be useful.

**Pain Points Addressed**: too few points of sales availabe, tedious manual creation of point of sale

## Why

- Enables platform to achieve adoption fast by leveraging available open-source data

## What

- Throws OsmNodeNotFoundException or OsmNodeMissingFieldsException in case of errors

### OSM XML specification
Utilize the specification below to understand the XML format and parse it correctly.
```
{{language}}

==Basics==
[[wikipedia:XML|XML]] is a so called meta format to provide human readable data interexchange formats. Various file formats use this data tree structure to embed their datas like XHTML, [[SVG]], ODT, ...

{|class="wikitable"
|-
!scope="col"|Pros
!scope="col"|Cons
|-
|
* human readable because of clear structure
* machine independent due to exact definitions, e.g. character sets, XML schema definitions, DTD, namespaces...
* ready to use parsers for general XML that can be customized for a concrete file format
* good compression ratio
|
* very huge files when decompressed
* might need to decompress before processing (data compression/decompression may be performed on the fly by the transport protocol such as HTTP)
* parsing ''may'' take a lot of time and memory resources, but only when using the full XML capabilities; basic XML (without DTD and namespaces) however is very fast and can be performed efficiently on the fly, without computing the DOM for the whole XML document (e.g. with simple SAX parsers)
|}

==OSM XML file format notes==

{{hatnote|No official .xsd Schema exists. See below and the [[OSM XML/XSD]], and [[OSM XML/DTD]] pages for details of unofficial attempts to define the format in those languages.}}

The major tools in the OSM universe use an XML format following a XML schema definition that was first used by the [[API]] only. Basically it is a list of instances of our [[Elements|data primitives]] ([[nodes]], [[ways]], and [[relations]]). 

===Example OSM XML file===
Here is a shortened example of a ''complete'' OSM XML file. Not every OSM XML file will contain all of these types of elements. See more in the notes below.

<source lang=xml>
<?xml version="1.0" encoding="UTF-8"?>
<osm version="0.6" generator="CGImap 0.0.2">
 <bounds minlat="54.0889580" minlon="12.2487570" maxlat="54.0913900" maxlon="12.2524800"/>
 <node id="298884269" lat="54.0901746" lon="12.2482632" user="SvenHRO" uid="46882" visible="true" version="1" changeset="676636" timestamp="2008-09-21T21:37:45Z"/>
 <node id="261728686" lat="54.0906309" lon="12.2441924" user="PikoWinter" uid="36744" visible="true" version="1" changeset="323878" timestamp="2008-05-03T13:39:23Z"/>
 <node id="1831881213" version="1" changeset="12370172" lat="54.0900666" lon="12.2539381" user="lafkor" uid="75625" visible="true" timestamp="2012-07-20T09:43:19Z">
  <tag k="name" v="Neu Broderstorf"/>
  <tag k="traffic_sign" v="city_limit"/>
 </node>
 ...
 <node id="298884272" lat="54.0901447" lon="12.2516513" user="SvenHRO" uid="46882" visible="true" version="1" changeset="676636" timestamp="2008-09-21T21:37:45Z"/>
 <way id="26659127" user="Masch" uid="55988" visible="true" version="5" changeset="4142606" timestamp="2010-03-16T11:47:08Z">
  <nd ref="292403538"/>
  <nd ref="298884289"/>
  ...
  <nd ref="261728686"/>
  <tag k="highway" v="unclassified"/>
  <tag k="name" v="Pastower Straße"/>
 </way>
 <relation id="56688" user="kmvar" uid="56190" visible="true" version="28" changeset="6947637" timestamp="2011-01-12T14:23:49Z">
  <member type="node" ref="294942404" role=""/>
  ...
  <member type="node" ref="364933006" role=""/>
  <member type="way" ref="4579143" role=""/>
  ...
  <member type="node" ref="249673494" role=""/>
  <tag k="name" v="Küstenbus Linie 123"/>
  <tag k="network" v="VVW"/>
  <tag k="operator" v="Regionalverkehr Küste"/>
  <tag k="ref" v="123"/>
  <tag k="route" v="bus"/>
  <tag k="type" v="route"/>
 </relation>
 ...
</osm>
</source>

See [[Elements]] for details of the object categories.<br>
See [[Map features]] about how real world objects are modeled and categorized.

===Contents===
*an XML suffix introducing the [[wikipedia:UTF-8|UTF-8]] character encoding for the file
*an osm element, containing the version of the [[API]] (and thus the features used) and the generator that distilled this file (e.g. an [[editor]] tool)
**a block of [[nodes]] containing especially the location in the [[WGS84]] reference system
***the tags of each node
**a block of [[ways]]
***the references to its nodes for each way
***the tags of each way
**a block of [[relations]]
***the references to its members for each relation 
***the tags of each relation

===Certainties and Uncertainties===
If you [[develop]] tools using this format, you can be certain that:
*blocks come in this order
*bounds will be on [[API]] and [[JOSM]] data

You can ''not'' be certain that:
*blocks are there (e.g. only nodes, no ways)
*blocks are sorted
*element IDs are non negative (Not in all osm files. Negative ids are used by editors for new elements)
*elements have to contain tags (Many elements do not. You will even come across [[Untagged unconnected node]]s)
*visible only if false and not in [[Planet.osm]]
*id or user name present (Not always, due to [[anonymous edits]] in a very early stage)
*[[Changesets]] have an attribute num_changes (This was abandoned from the history export tool because of inconsistencies)
*version ordering is sequential (doesn't have to be)

[[JOSM]] uses an 'action' attribute instead of timestamp, version or changeset for new objects

Some [[#Flavours|flavours]] might have other restrictions.

==Tools==
See [[Planet.osm]] and [[Import]], [[Export]], [[Convert]]

==Flavours==

There are a few different file formats currently in use, all with slightly different goals.
* [[API]]
* [[JOSM file format]]
* [[osmChange]]
* [[planetdiff]]
* [[Osmdiff]]

===JOSM file format===
{{Main|JOSM file format}}

The file format was designed by the author of JOSM. It basically is a logical extension of the data sent from the server. What it adds is an indication of the origin of the data and the bounding box it comes from (if possible). It is actually more of a storage format of data downloaded along with changes made by the user.

{| class="wikitable"
!pros!!cons
|-
|
* Supports placeholders
* Indicates the source of the data
|
* not streamable, must read the whole file prior to applying
|}

Supported by:
* [[JOSM]]
* [[Bulk_upload.py]] (read-only)
* [[:Geo::OSM library]]
* [[osmconvert]]
* [[osmfilter]]
* [[Vespucci]]

===osmChange===
{{Main|OsmChange}}
OsmChange is a file format was created by the author of [[osmosis]] and is a more general format for representing changes.

{| class="wikitable"
!pros!!cons
|-
|
* Streamable
When sorted properly this file is a continuous stream of changes that can be played in order. In osmosis the option '''--sort-change''' will put the change into streamable order.
|
* Doesn't indicate source of data
|}

Placeholders are proposed as an extension though they are not widely supported.

Supported by:
* [[Osmosis]]
* [[Bulk_upload.py]] (read-only)
* [[:Geo::OSM library]]
* [[osmconvert]]
* [[osmchange (program)|osmchange]] (read-only)
* [[osmfilter]]
* [[Vespucci]] import and export.

=== Overpass API <code>out geom</code> ===
When specifying <code>[[Overpass API/Overpass QL#out|out geom]]</code>, the full geometry is added to each object.<ref>[https://overpass-api.de/api/interpreter?data=relation(id:56688);out%20geom; https://overpass-api.de/api/interpreter?data=relation(id:56688);out%20geom;]</ref><source lang="xml">
<?xml version="1.0" encoding="UTF-8"?>
<osm version="0.6" generator="Overpass API 0.7.59.5 b201bb38">
<note>The data included in this document is from www.openstreetmap.org. The data is made available under ODbL.</note>
<meta osm_base="2023-04-14T22:35:07Z"/>

  <relation id="56688">
    <bounds minlat="54.0655806" minlon="12.1249646" maxlat="54.1028223" maxlon="12.2768140"/>
    <member type="way" ref="430830028" role="">
      <nd lat="54.0791021" lon="12.1319813"/>
      <nd lat="54.0790727" lon="12.1320578"/>
    </member>
    <member type="way" ref="526464709" role="">
      <nd lat="54.0791475" lon="12.1318771"/>
      <nd lat="54.0791021" lon="12.1319813"/>
    </member>
    ...
    <member type="way" ref="73095080" role="forward">
      <nd lat="54.0802900" lon="12.1265417"/>
      <nd lat="54.0807860" lon="12.1269489"/>
    </member>
    <member type="way" ref="60809462" role="forward">
      <nd lat="54.0807860" lon="12.1269489"/>
      <nd lat="54.0810162" lon="12.1271403"/>
      <nd lat="54.0811261" lon="12.1272229"/>
      <nd lat="54.0814445" lon="12.1274366"/>
    </member>
    <member type="node" ref="294942404" role="" lat="54.0870546" lon="12.2223528"/>
    <member type="node" ref="317225863" role="" lat="54.0864639" lon="12.2278818"/>
    <member type="node" ref="317225865" role="" lat="54.0868612" lon="12.2278989"/>
    ...
    <member type="node" ref="784633801" role="" lat="54.0734381" lon="12.2127502"/>
    <member type="node" ref="249673494" role="" lat="54.0661787" lon="12.2241369"/>
    <member type="node" ref="2329122420" role="" lat="54.0900099" lon="12.2540383"/>
    <tag k="name" v="Bus 123"/>
    <tag k="network" v="Verkehrsverbund Warnow"/>
    <tag k="network:short" v="VVW"/>
    <tag k="network:wikidata" v="Q2516492"/>
    <tag k="network:wikipedia" v="de:Verkehrsverbund Warnow"/>
    <tag k="operator" v="rebus Regionalbus Rostock GmbH"/>
    <tag k="public_transport:version" v="1"/>
    <tag k="ref" v="123"/>
    <tag k="route" v="bus"/>
    <tag k="type" v="route"/>
  </relation>

</osm>

</source>

==Technical features of change formats==
This a list of things that are desirable in a change file format for exampe [[change sets]]
====placeholders====
Placeholders are a feature where objects that are created in the file can be used in the creation of the objects that depend on them. So a single file can create two nodes and join them with a segment without knowing beforehand the final IDs.

Objects with placeholders don't have any version number, creation timestamp, or user id or changeset associated to them (these properties will be set by the server and used to detect and report conflicting or desynchronized changes).

Placeholder IDs must be negative integers, assigned arbitrarily, but whose scope is local to the data file being sent to the data server where they will be created. After they are successfully created, the server returns the final IDs assigned to each object as positive integers, mapped for each submitted placeholder successfully created (as well as their initial version number, creation timestamp, and changeset identifier within which which they were created), so that the client can replace and use these final IDs in further requests when referencing the same objects.

====indication of origin====
IDs used in OSM files can not be shared between servers. IDs are allocated by the server, not by clients. Thus it is useful if the change file indicates the  source of any IDs used in the file.
====streamable====
Whether the file can normally be processed in a stream, without breaking referential integrity.

==See also==
* [[API]]
* [[OSM XML/XSD]]
** [[API v0.6/XSD]]
* [[OSM XML/DTD]]
** [[API v0.6/DTD]]
* [[Daily update an OSM XML file]]
* see also Category OSM API below

[[Category:OSM API]]
[[Category:OSM file formats]]
[[Category:Development]]
[[Category:Output formats]]
[[Category:Translate to Spanish]]
```


## Documentation & References

MUST READ - Include the following information in your context window.

The `README.md` file at the root of the project contains setup instructions and example API calls.

This Java Spring Boot application is structured as a multi-module Maven project following the ports-and-adapters architectural pattern.
There are the following submodules:

`api` - Maven submodule for controller adapter.

`application` - Maven submodule for Spring Boot application, test data import, and system tests.

`data` - Maven submodule for data adapter.

`domain` - Maven submodule for domain model, main business logic, and ports.
