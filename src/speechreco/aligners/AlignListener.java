/*
This source code is copyrighted by Christophe Cerisara, CNRS, France.

It is licensed under the terms of the INRIA Cecill-C licence, as described in:
http://www.cecill.info/licences/Licence_CeCILL-C_V1-en.html
*/

package speechreco.aligners;

public interface AlignListener {
	/**
	 * motidx est le dernier mot aligne
	 */
	public void newAlign(int firstmotidx, int lastmotidx);
	/**
	 * lorsque l'aligneur n'a pas trouve d'alignement
	 */
	public void noAlignFound();
	/**
	 * appele uniquement quand la reco est terminee parce que la fin du fichier a ete atteinte
	 */
	public void recoFinished();
}
