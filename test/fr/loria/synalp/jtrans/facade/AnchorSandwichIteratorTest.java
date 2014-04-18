package fr.loria.synalp.jtrans.facade;

import fr.loria.synalp.jtrans.elements.Anchor;
import fr.loria.synalp.jtrans.elements.Element;
import fr.loria.synalp.jtrans.elements.Word;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

public class AnchorSandwichIteratorTest {

	@Test
	public void testEmptyElementList() {
		AnchorSandwichIterator i = new AnchorSandwichIterator(
				new ArrayList<Element>());
		assertFalse(i.hasNext());
	}


	@Test
	public void testNoAnchorsAtExtremities() {
		List<Element> L = new ArrayList<Element>();
		L.add(new Word("a"));
		L.add(new Word("b"));
		L.add(new Word("c"));
		L.add(Anchor.timedAnchor(10)); // #3
		L.add(new Word("d"));
		L.add(new Word("e"));
		L.add(Anchor.timedAnchor(15)); // #6
		L.add(new Word("f"));
		L.add(new Word("g"));

		AnchorSandwichIterator i = new AnchorSandwichIterator(L);

		assertTrue(i.hasNext());
		assertEquals(L.subList(0, 3), i.next());
		assertEquals(L.subList(4, 6), i.next());
		assertEquals(L.subList(7, 9), i.next());
		assertFalse(i.hasNext());
	}


	@Test
	public void testLeadingAnchor() {
		List<Element> L = new ArrayList<Element>();
		L.add(Anchor.timedAnchor(10));
		L.add(new Word("d"));
		L.add(new Word("e"));
		L.add(new Word("f"));
		L.add(new Word("g"));

		AnchorSandwichIterator i = new AnchorSandwichIterator(L);

		assertTrue(i.hasNext());
		assertEquals(L.subList(1, L.size()), i.next());
		assertFalse(i.hasNext());
	}


	@Test
	public void testTrailingAnchor() {
		List<Element> L = new ArrayList<Element>();
		L.add(new Word("d"));
		L.add(new Word("e"));
		L.add(new Word("f"));
		L.add(new Word("g"));
		L.add(Anchor.timedAnchor(15));

		AnchorSandwichIterator i = new AnchorSandwichIterator(L);

		assertTrue(i.hasNext());
		assertEquals(L.subList(0, 4), i.next());
		assertTrue(i.next().isEmpty());
		assertFalse(i.hasNext());
	}


	@Test
	public void testEmpty() {
		List<Element> L = new ArrayList<Element>();
		AnchorSandwichIterator i = new AnchorSandwichIterator(L);
		assertFalse(i.hasNext());
	}


	@Test
	public void testAnchorsOnly() {
		List<Element> L = new ArrayList<Element>();
		L.add(Anchor.timedAnchor(5));
		L.add(Anchor.timedAnchor(10));
		L.add(Anchor.timedAnchor(15));
		L.add(Anchor.timedAnchor(20));

		AnchorSandwichIterator iter = new AnchorSandwichIterator(L);

		for (int i = 0; i < L.size()-1; i++) {
			AnchorSandwich s = iter.next();
			assertTrue(s.isEmpty());
			assertEquals(L.get(i), s.getInitialAnchor());
			assertEquals(L.get(i+1), s.getFinalAnchor());
		}

		assertFalse(iter.hasNext());
	}


	@Test
	public void testEmptySandwich() {
		List<Element> L = new ArrayList<Element>();

		L.add(Anchor.timedAnchor(5));
		L.add(new Word("abc"));
		L.add(Anchor.timedAnchor(10));
		L.add(Anchor.timedAnchor(15));
		L.add(new Word("def"));
		L.add(Anchor.timedAnchor(20));

		AnchorSandwichIterator iter = new AnchorSandwichIterator(L);
		AnchorSandwich s;

		assertTrue(iter.hasNext());
		s = iter.next();
		assertEquals(L.get(0), s.getInitialAnchor());
		assertEquals(L.get(2), s.getFinalAnchor());
		assertEquals(L.subList(1, 2), s);

		assertTrue(iter.hasNext());
		s = iter.next();
		assertEquals(L.get(2), s.getInitialAnchor());
		assertEquals(L.get(3), s.getFinalAnchor());
		assertTrue(s.isEmpty());

		assertTrue(iter.hasNext());
		s = iter.next();
		assertEquals(L.get(3), s.getInitialAnchor());
		assertEquals(L.get(5), s.getFinalAnchor());
		assertEquals(L.subList(4, 5), s);

		assertFalse(iter.hasNext());
	}

}
