package facade;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.*;
import java.io.IOException;
import java.util.ArrayList;

/**
 * TRS Loader.
 *
 * The constructor parses a TRS file. If parsing was successful, text and anchor info
 * can be retrieved through the public final attributes.
 */
class TRSLoader {

	/**
	 * Time anchor. Matches a "Sync" tag in TRS files.
	 */
	class Anchor {
		/** Position of the anchor in the text. */
		int character;

		/** Time */
		float seconds;

		private Anchor(int c, float s) {
			character = c;
			seconds = s;
		}
	}


	/**
	 * Raw text contained in the Turn tags.
	 */
	public final String text;


	/**
	 * Time anchors ("Sync" tags) contained in the Turn tags.
	 */
	public final ArrayList<Anchor> anchors;


	/**
	 * Parse a TRS file.
	 */
	public TRSLoader(String path) throws ParserConfigurationException, IOException, SAXException {
		Document doc = newXMLDocumentBuilder().parse(path);
		StringBuffer buffer = new StringBuffer();
		ArrayList<Anchor> anchorList = new ArrayList<Anchor>();

		// current last anchor
		Anchor lastAnchor = null;

		// prefixed to the contents of a new text node
		String prefixWhitespace = "";

		// end time of last turn
		float lastEnd = -1f;

		// Extract relevant information (speech text, Sync tags...) from Turn tags.
		NodeList turnList = doc.getElementsByTagName("Turn");

		for (int i = 0; i < turnList.getLength(); i++) {
			Element turn = (Element)turnList.item(i);
			Node child = turn.getFirstChild();

			float endTime = Float.parseFloat(turn.getAttribute("endTime"));
			if (endTime > lastEnd)
				lastEnd = endTime;

			while (null != child) {
				String name = child.getNodeName();

				// Speech text
				if (name.equals("#text")) {
					String text = child.getTextContent().trim();
					if (!text.isEmpty()) {
						if (buffer.length() > 0)
							buffer.append(prefixWhitespace);
						buffer.append(text);
					}
					prefixWhitespace = " ";
				}

				// Anchor. Placed on the last character in the word *PRECEDING* the sync point
				else if (name.equals("Sync")) {
					int character = buffer.length();
					float second = Float.parseFloat(((Element)child).getAttribute("time"));

					if (null != lastAnchor && character == lastAnchor.character) {
						// No text added between two anchors.
						// Adjust last anchor instead of adding a new one
						lastAnchor.character = character;
					} else {
						lastAnchor = new Anchor(character, second);
						anchorList.add(lastAnchor);
					}

					prefixWhitespace = "\n";
				}

				// Ignore unknown tag
				else {
					System.out.println("TRS WARNING: Ignoring inknown tag " + name);
					prefixWhitespace = " ";
				}

				// Onto next Turn child
				child = child.getNextSibling();
			}
		}

		// Fake anchor after last turn so that the whole speech gets aligned
		anchorList.add(new Anchor(buffer.length(), lastEnd));

		text = buffer.toString();
		anchors = anchorList;
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
}
