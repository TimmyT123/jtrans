package fr.loria.synalp.jtrans.markup.in.preprocessors;

import fr.loria.synalp.jtrans.markup.in.ParsingException;
import fr.loria.synalp.jtrans.project.Comment;
import fr.loria.synalp.jtrans.project.TurnProject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.List;
import java.util.ListIterator;

/**
 * Adds Who tags to Transcriber files that use the TCOF convention for
 * overlapping speech.
 *
 * The TCOF corpus does not use Who tags, which is the standard Transcriber way
 * of denoting overlapping speech. Instead, TCOF denotes overlaps with angle
 * brackets across two Turns. In the following example, the words "four five
 * six" and "seven eight nine" are spoken simultaneously.
 *
 * <pre>
 * {@code
 * <Turn speaker="spk1" ...>
 *     <Sync time="1.234"/> one two three &lt; four five six
 * </Turn>
 *
 * <Turn speaker="spk2" ...>
 *     <Sync time="5.678"/> seven eight nine &gt; ten eleven twelve
 * </Turn>
 * }</pre>
 *
 * This tool would transform the example above into multi-speaker Turns, which,
 * as far as JTrans is concerned, is more manageable Transcriber markup.
 *
 * <pre>
 * {@code
 * <Turn speaker="spk1" ...>
 *     <Sync time="1.234"/> one two three
 * </Turn>
 *
 * <Turn speaker="spk1 spk2" ...>
 *     <Sync time="5.678"/>
 *     <Who nb="1"/> &lt; four five six
 *     <Who nb="2"/> seven eight nine &gt; ten eleven twelve
 * </Turn>
 * }</pre>
 */
public class TCOFWhoifier extends TRSPreprocessor {

	public static void main(String[] args) throws Exception {
		new TCOFWhoifier().transform(args);
	}


	@Override
	public String getFormat() {
		return "Transcriber (TCOF conventions)";
	}


	public void preprocess(Document doc) throws ParsingException {
		String previousSpeaker = null;
		Element reportScrapped = null;
		List<Node> reportOverlap = null;

		NodeList turnList = doc.getElementsByTagName("Turn");
		for (int turnNo = 0; turnNo < turnList.getLength(); turnNo++) {
			Element turn = (Element)turnList.item(turnNo);

			boolean hangingOverlap = reportOverlap != null;
			final boolean multiSpeakerTurn = hangingOverlap;

			// "Empty" means the turn does not contain any significant children.
			// Sync tags and whitespace #text are not significant by themselves.
			boolean emptyTurn = reportOverlap == null && reportScrapped == null;

			String singleSpeaker = turn.getAttribute("speaker");
			if (singleSpeaker.isEmpty() || 1 != singleSpeaker.split(" ").length)
				throw new ParsingException("speaker count != 1. If >0, already whoified");

			assert reportOverlap == null || reportScrapped == null;

			if (reportOverlap != null) {
				if (!previousSpeaker.equals(singleSpeaker)) {
					turn.setAttribute("speaker", previousSpeaker + " " + singleSpeaker);
				} else {
					// Ignore the overlap if it's set on the same speaker
					// (happens quite often in TCOF)
					System.err.println("TRS WARNING: overlap on same speaker! " + singleSpeaker);
					for (Node n: reportOverlap) {
						turn.insertBefore(n, turn.getFirstChild());
					}
					reportOverlap = null;
					hangingOverlap = false;
				}
			}

			if (reportScrapped != null) {
				if (singleSpeaker.equals(previousSpeaker)) {
					NodeList rscn = reportScrapped.getChildNodes();
					for (int j = rscn.getLength()-1; j >= 0; j--)
						turn.insertBefore(rscn.item(j), turn.getFirstChild());

					turn.setAttribute("startTime", reportScrapped.getAttribute("startTime"));
				} else {
					turn = (Element)turn.getParentNode().insertBefore(reportScrapped, turn);
					singleSpeaker = turn.getAttribute("speaker");
				}
				reportScrapped = null;
			}

			// We may have changed our turn

			Node child = turn.getFirstChild();
			previousSpeaker = singleSpeaker;

			turnChildLoop:
			while (null != child) {
				String name = child.getNodeName();
				String text = child.getTextContent();

				if (name.equals("Sync") && reportOverlap != null) {
					Node nextChild = child.getNextSibling();

					Element who1 = doc.createElement("Who");
					who1.setAttribute("nb", ""+1);
					Element who2 = doc.createElement("Who");
					who2.setAttribute("nb", ""+2);

					turn.insertBefore(who1, nextChild);
					for (Node n: reportOverlap)
						turn.insertBefore(n, nextChild);
					turn.insertBefore(who2, nextChild);

					reportOverlap = null;
					hangingOverlap = false;
					child = nextChild;
				}

				else if (name.equals("Sync") && reportOverlap == null && multiSpeakerTurn) {
					String syncTime =  ((Element)child).getAttribute("time");

					reportScrapped = doc.createElement("Turn");
					reportScrapped.setAttribute("endTime", turn.getAttribute("endTime"));
					reportScrapped.setAttribute("startTime", syncTime);
					reportScrapped.setAttribute("speaker",   singleSpeaker);
					for (Node n: scrap(child))
						reportScrapped.appendChild(n);

					turn.setAttribute("endTime", syncTime);
					break;
				}

				else if (name.equals("#text") && text.contains("<")) {
					if (reportOverlap != null)
						throw new ParsingException("overlap already ongoing before '" + text + "'");

					/* Verify that the rest of the Turn doesn't contain any Sync
					tags; if it does, ignore the overlap.

					After an overlap mark, we expect some text, then a new Turn
					that immediately contains a Sync tag to signal when the 2nd
					speaker starts speaking. Putting a Sync tag after an overlap
					mark without opening a new Turn is meaningless (yet it
					happens once in a while in TCOF). */
					for (Node n = child; null != n; n = n.getNextSibling()) {
						if (n.getNodeName().equals("Sync")) {
							System.err.println("TRS WARNING: illegal Sync after overlap mark!");
							child = n;
							continue turnChildLoop;
						}
					}

					int chevronPos = text.indexOf('<');
					String beforeChevron = text.substring(0, chevronPos);
					child.setTextContent(beforeChevron);

					reportOverlap = scrap(child.getNextSibling());
					reportOverlap.add(0, doc.createTextNode(text.substring(chevronPos)));

					// Don't leave an empty turn
					if (emptyTurn && beforeChevron.trim().isEmpty()) {
						turn.getParentNode().removeChild(turn);
						turnNo--;
					}

					break;
				}

				else {
					if (!name.equals("Sync") && (!name.equals("#text") || !text.trim().isEmpty()))
						emptyTurn = false;

					child = child.getNextSibling();
				}
			}

			if (hangingOverlap)
				throw new ParsingException("hanging overlap - missing Sync tag?");
		}
	}


	@Override
	protected void postprocess(TurnProject p) throws ParsingException {
		for (int t = 0; t < p.turns.size(); t++) {
			TurnProject.Turn turn = p.turns.get(t);

			boolean turnContainsOEM = false;

			for (int spkID = 0; spkID < p.speakerCount(); spkID++) {

				for (int elID = 0; elID < turn.elts.get(spkID).size(); elID++) {
					fr.loria.synalp.jtrans.project.Element el = turn.elts.get(spkID).get(elID);

					if (el instanceof Comment) {
						Comment comment = (Comment) el;
						if (comment.getType() == Comment.Type.OVERLAP_END_MARK) {
							if (turnContainsOEM) {
								throw new ParsingException("illegal overlap end mark");
							}
							t = breakUpOverlapEnd(p, t, spkID, elID);
							turnContainsOEM = true;
						}
					}
				}
			}
		}
	}


	/**
	 * @return new ID of the current turn
	 */
	private int breakUpOverlapEnd(TurnProject p, int turnID, int spkID, int elID) {
		ListIterator<fr.loria.synalp.jtrans.project.Element> elItr =
				p.turns.get(turnID).elts.get(spkID).listIterator(elID + 1);

		TurnProject.Turn newTurn = p.new Turn();

		final boolean empty = !elItr.hasNext();

		while (elItr.hasNext()) {
			newTurn.add(spkID, elItr.next());
			elItr.remove();
		}

		if (!empty) {
			newTurn.end = p.turns.get(turnID).end;
			p.turns.get(turnID).end = null;
			p.turns.add(turnID+1, newTurn);
			return turnID+1;
		}

		return turnID;
	}

}
