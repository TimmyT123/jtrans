/*
This source code is copyrighted by Christophe Cerisara, CNRS, France.

It is licensed under the terms of the INRIA Cecill-C licence, as described in:
http://www.cecill.info/licences/Licence_CeCILL-C_V1-en.html
*/

/*
Copyright Christophe Cerisara, Josselin Pierre (1er septembre 2008)

cerisara@loria.fr

Ce logiciel est un programme informatique servant e aligner un
corpus de parole avec sa transcription textuelle.

Ce logiciel est regi par la licence CeCILL-C soumise au droit franeais et
respectant les principes de diffusion des logiciels libres. Vous pouvez
utiliser, modifier et/ou redistribuer ce programme sous les conditions
de la licence CeCILL-C telle que diffusee par le CEA, le CNRS et l'INRIA 
sur le site "http://www.cecill.info".

En contrepartie de l'accessibilite au code source et des droits de copie,
de modification et de redistribution accordes par cette licence, il n'est
offert aux utilisateurs qu'une garantie limitee.  Pour les memes raisons,
seule une responsabilite restreinte pese sur l'auteur du programme,  le
titulaire des droits patrimoniaux et les concedants successifs.

A cet egard  l'attention de l'utilisateur est attiree sur les risques
associes au chargement,  e l'utilisation,  e la modification et/ou au
developpement et e la reproduction du logiciel par l'utilisateur etant 
donne sa specificite de logiciel libre, qui peut le rendre complexe e 
manipuler et qui le reserve donc e des developpeurs et des professionnels
avertis possedant  des  connaissances  informatiques approfondies.  Les
utilisateurs sont donc invites e charger  et  tester  l'adequation  du
logiciel e leurs besoins dans des conditions permettant d'assurer la
securite de leurs systemes et ou de leurs donnees et, plus generalement, 
e l'utiliser et l'exploiter dans les memes conditions de securite. 

Le fait que vous puissiez acceder e cet en-tete signifie que vous avez 
pris connaissance de la licence CeCILL-C, et que vous en avez accepte les
termes.
*/

package plugins.text.regexp.graphique;


import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;
import javax.swing.filechooser.FileFilter;

import plugins.text.TexteEditor;
import plugins.text.regexp.TypeElement;
import plugins.text.regexp.controler.ActionAddRegexp;
import plugins.text.regexp.controler.ActionDeleteRegexp;

/** Fenetre d'edition des regexp */
public class RegExpFrame extends JFrame {

	//---------------- Private Fields ---------------
	private TexteEditor texteEditor;
	private JTabbedPane tabbedPane;
	
	private FileFilter filterXML;
	
	//--------------------- Constructor -----------------
	public RegExpFrame(TexteEditor textEdit){
		setTitle("Edition des differents types");
		texteEditor = textEdit;
		
		initFileFilters();
		
		tabbedPane = new JTabbedPane(JTabbedPane.LEFT);

		remplirTabbedPane();

		getContentPane().add(tabbedPane);
		this.setSize(500,300);
		this.setVisible(true);
	}//constructor
	
	
	private void initFileFilters(){
		filterXML = new FileFilter(){
			@Override
			public boolean accept(File arg0) {
				String path;
				try {
					if(arg0.isDirectory()) return true;

					path = arg0.getCanonicalPath();
					int indice = path.lastIndexOf('.');
					if (indice == -1) return false;

					String extension = path.substring(indice);
					return extension.equalsIgnoreCase(".xml");

				} catch (IOException e) {
					e.printStackTrace();
				}
				return false;
			}

			@Override
			public String getDescription() {
				return ".xml file";
			}
		};
	}
	
	
	private void remplirTabbedPane(){
		tabbedPane.add(creerPanelOptions(),"Options");
		
		JPanel pan;
		JList liste;
		//Un panel pour chaque element defini.
		for(TypeElement typeElement : texteEditor.getListeTypes()){
			pan = new JPanel();
			pan.setLayout(new BorderLayout());
			
			liste = new JList(typeElement.getRegexp());
			pan.add(liste,BorderLayout.CENTER);
			
			//---- Creation du panel des boutons ----------
			JPanel panBoutons = new JPanel();
			panBoutons.setLayout(new BoxLayout(panBoutons,BoxLayout.X_AXIS));
			 
			JButton boutonAdd = new JButton("Ajouter");
			boutonAdd.addActionListener(new ActionAddRegexp(typeElement, liste));
			panBoutons.add(boutonAdd);
			
			JButton boutonDel = new JButton("Supprimer");
			boutonDel.addActionListener(new ActionDeleteRegexp(typeElement, liste));
			panBoutons.add(boutonDel);
			
			panBoutons.add(Box.createHorizontalGlue());
			panBoutons.add(new JButtonCouleurRegexp(typeElement));
			
			pan.add(panBoutons,BorderLayout.SOUTH);
			
			tabbedPane.add(pan,typeElement.getNom());
		}//for
		
	}//remplirTabbedPane
	
	
	private JPanel creerPanelOptions(){
		JPanel pan = new JPanel(new BorderLayout());
		
		JTextPane texteExplicatif = new JTextPane();
		StringBuilder strBuilder = new StringBuilder();
		strBuilder.append("Cette fenetre vous permet d'editer les expressions regulieres ");
		strBuilder.append("qui permettent de reconnaitre les differentes syntaxes presentes dans ");
		strBuilder.append("les fichier textes que vous allez ouvrir.\n");
		strBuilder.append("Ces expressions regulieres utilisent la syntaxe d'expression du langage JAVA.\n");
		strBuilder.append("Une description de la syntaxe est disponible sur la page : \n");
		strBuilder.append("http://java.sun.com/javase/6/docs/api/java/util/regex/Pattern.html \n\n");
		strBuilder.append("Attention, ne modifiez cela que si vous savez ce que vous faites !");
		texteExplicatif.setText(strBuilder.toString());
		pan.add(new JScrollPane(texteExplicatif,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
												JScrollPane.HORIZONTAL_SCROLLBAR_NEVER)
				,BorderLayout.CENTER);
		
		JPanel panelBoutons = new JPanel();
		panelBoutons.setLayout(new BoxLayout(panelBoutons,BoxLayout.X_AXIS));
		
		JButton saveregexps = new JButton("Sauver");
		saveregexps.setToolTipText("Sauvegarder les expressions regulieres dans un fichier");
		saveregexps.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JFileChooser filechooser = new JFileChooser();
				filechooser.setFileFilter(filterXML);
				int returnVal = filechooser.showSaveDialog(null);
				if(returnVal == JFileChooser.APPROVE_OPTION) {
					File file = filechooser.getSelectedFile();
					try {
						texteEditor.saveRegexpTypesAsFile(file.getAbsolutePath());
					} catch (Exception ee) {
						ee.printStackTrace();
					}
				}
			}
		});
		panelBoutons.add(saveregexps);
		
		JButton loadregexp = new JButton("Ouvrir");
		loadregexp.setToolTipText("Charger les expressions regulieres depuis un fichier");
		loadregexp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JFileChooser filechooser = new JFileChooser();
				filechooser.setFileFilter(filterXML);
				int returnVal = filechooser.showOpenDialog(null);
				if(returnVal == JFileChooser.APPROVE_OPTION) {
					File file = filechooser.getSelectedFile();
					try {
						texteEditor.loadRegexpTypesFromFile(file.getAbsolutePath());
						tabbedPane.removeAll();
						remplirTabbedPane();
					} catch (Exception ee) {
						ee.printStackTrace();
					}
				}
			}
		});
		
		panelBoutons.add(loadregexp);
		
		pan.add(panelBoutons,BorderLayout.SOUTH);
		
		return pan;
	}//creerPanelOptions
	
}//Class RegExpFrame
