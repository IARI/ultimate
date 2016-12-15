package de.uni_freiburg.informatik.ultimate.rcfg.serializer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.CodeBlock;

public class ICFGOutput {
	// private static final String LINEBREAK =
	// System.getProperty("line.separator");
	/**
	 * The file writer.
	 */
	private final PrintWriter mWriter;
	private final Document mXMLTemplate;
	private final ILogger mLogger;

	public ICFGOutput(PrintWriter mWriter, Document mXMLTemplate, ILogger mLogger) {
		this.mWriter = mWriter;
		this.mXMLTemplate = mXMLTemplate;
		this.mLogger = mLogger;
	}

	public void printRCFG(IIcfg<IcfgLocation> root) {

		XPathFactory xPathfactory = XPathFactory.newInstance();
		XPath xpath = xPathfactory.newXPath();
		Node nodes, edges;

		try {
			nodes = (Node) xpath.compile("//nodes").evaluate(mXMLTemplate, XPathConstants.NODE);
			if (nodes == null) {
				mLogger.fatal("could not find <nodes> element in xml template.");
				return;
			}
			edges = (Node) xpath.compile("//edges").evaluate(mXMLTemplate, XPathConstants.NODE);
			if (edges == null) {
				mLogger.fatal("could not find <edges> element in xml template.");
				return;
			}
		} catch (XPathExpressionException e) {
			mLogger.fatal("could not compile XPathExpression");
			e.printStackTrace();
			return;
		}

		ICFGTraverser graph = new ICFGTraverser(root);
		Element xmlNode, xmlEdge;
		String label, id;
		int i = 0, j = 0;
		Map<IcfgLocation, String> nodeMap = new HashMap<>();
		for (IcfgLocation node : graph.getNodes()) {
			label = node.toString();
			id = "n" + i++;
			nodeMap.put(node, id);
			xmlNode = mXMLTemplate.createElement("node");
			xmlNode.setAttribute("id", id);
			xmlNode.setAttribute("label", label);
			nodes.appendChild(xmlNode);
		}
		for (IcfgEdge edge : graph.getEdges()) {
			if (edge instanceof CodeBlock) {
				CodeBlock cb = (CodeBlock) edge;
				label = cb.getPrettyPrintedStatements();
			} else {
				label = edge.toString();
			}
			xmlEdge = mXMLTemplate.createElement("edge");
			id = "e" + j++;
			xmlEdge.setAttribute("id", id);
			xmlEdge.setAttribute("label", label);
			xmlEdge.setAttribute("type", "directed");
			xmlEdge.setAttribute("source", nodeMap.get(edge.getSource()));
			xmlEdge.setAttribute("target", nodeMap.get(edge.getTarget()));
			edges.appendChild(xmlEdge);
		}

		mWriter.write(getStringFromDoc(mXMLTemplate));
	}

	public static String getStringFromDoc(org.w3c.dom.Document doc) {
		try {
			DOMSource domSource = new DOMSource(doc);
			StringWriter writer = new StringWriter();
			StreamResult result = new StreamResult(writer);
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
			transformer.transform(domSource, result);
			writer.flush();
			return writer.toString();
		} catch (TransformerException ex) {
			ex.printStackTrace();
			return null;
		}
	}

}
