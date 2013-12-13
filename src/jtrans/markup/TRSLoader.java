package jtrans.markup;

import jtrans.elements.*;
import jtrans.facade.Project;
import jtrans.facade.Track;
import org.w3c.dom.*;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.*;
import java.io.*;
import java.util.*;

/**
 * Parser for the Transcriber file format.
 */
public class TRSLoader implements MarkupLoader {

	public Project parse(File file)
			throws ParsingException, IOException
	{
		Project project = new Project();
		Document doc;

		// Map of Transcriber's speaker IDs to JTrans tracks
		Map<String, Track> trackIDMap = new HashMap<String, Track>();

		try {
			doc = newXMLDocumentBuilder().parse(file);
		} catch (ParserConfigurationException ex) {
			ex.printStackTrace();
			throw new ParsingException(ex.toString());
		} catch (SAXException ex) {
			throw new ParsingException(ex.toString());
		}

		// end time of last turn
		float lastEnd = -1f;

		// Create speaker tracks and build ID map
		for (Node spk = doc.getElementsByTagName("Speakers").item(0).getFirstChild();
			 null != spk;
			 spk = spk.getNextSibling())
		{
			if (!spk.getNodeName().equals("Speaker"))
				continue;

			Element el = (Element)spk;
			String trsID   = el.getAttribute("id");
			String name    = el.getAttribute("name");

			Track newTrack = new Track(name);
			project.tracks.add(newTrack);
			trackIDMap.put(trsID, newTrack);
		}

		// Extract relevant information (speech text, Sync tags...) from Turn tags.
		NodeList turnList = doc.getElementsByTagName("Turn");
		for (int i = 0; i < turnList.getLength(); i++) {
			Element turn = (Element)turnList.item(i);
			Node child = turn.getFirstChild();
			Track currentTrack = trackIDMap.get(turn.getAttribute("speaker"));

			float endTime = Float.parseFloat(turn.getAttribute("endTime"));
			if (endTime > lastEnd)
				lastEnd = endTime;

			while (null != child) {
				String name = child.getNodeName();

				// Speech text
				if (name.equals("#text")) {
					String text = RawTextLoader.normalizeText(child.getTextContent().trim());
					if (!text.isEmpty()) {
						currentTrack.elts.addAll(RawTextLoader.parseString(text, project.types));
					}
				}

				// Anchor. Placed on the last character in the word *PRECEDING* the sync point
				else if (name.equals("Sync")) {
					currentTrack.elts.add(new Anchor(
							Float.parseFloat(((Element) child).getAttribute("time"))));
				}

				else if (name.equals("Comment")) {
					currentTrack.elts.add(new jtrans.elements.Comment(
							((Element)child).getAttribute("desc")));
				}

				// Ignore unknown tag
				else {
					System.out.println("TRS WARNING: Ignoring inknown tag " + name);
				}

				// Onto next Turn child
				child = child.getNextSibling();
			}
		}

		for (Track track : project.tracks)
			track.elts.add(new Anchor(lastEnd));
		return project;
	}


	/**
	 * Return a DocumentBuilder suitable to parsing a TRS file.
	 */
	private static DocumentBuilder newXMLDocumentBuilder() throws ParserConfigurationException {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setValidating(false);
		dbf.setNamespaceAware(true);
		dbf.setFeature("http://xml.org/sax/features/namespaces", false);
		dbf.setFeature("http://xml.org/sax/features/validation", false);
		dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
		dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		return dbf.newDocumentBuilder();
	}


	public String getFormat() {
		return "Transcriber (.TRS)";
	}
}
