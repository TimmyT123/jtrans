/*
This source code is copyrighted by Christophe Cerisara, CNRS, France.

It is licensed under the terms of the INRIA Cecill-C licence, as described in:
http://www.cecill.info/licences/Licence_CeCILL-C_V1-en.html
*/

package plugins.phonetiseurNomPropre;

import utils.BDLex;
import utils.PronunciationsLexicon;

/**
 * Ce Main est un exemple d'utilisation du phonetiseur via la facade
 */
public class MainFacade {

    public static void main(String[] args) throws Exception {
        String fichierSauvGraphemesPhonemesMatriceProba = "/home/jean/StageLORIA/PourExecution/===-MatricesProbas_090812-===.sauv";
        String repertoireARFF = "/home/jean/StageLORIA/PourExecution/ARFF/";
        String repertoireSauvClassifieurs = "/home/jean/StageLORIA/PourExecution/Models/";

        PronunciationsLexicon pL = new BDLex("/home/jean/StageLORIA/Dicos/BDLexNomsPropres", "");
        String fichierMotsAApprendreDansLeDico = "/home/jean/StageLORIA/ListesDeMots/motsAApprendre_dicoNomsPropre_TRAIN.txt";

        String nomFichierProbasLangues = "/home/jean/StageLORIA/ListesDeMots/nompropres.vecteurs";

        /**********************************************/
        /*** Etape 1 (instanciation du phonetiseur) ***/
        /**********************************************/
        // Note : les repertoirs pour les fichiers ARFF et les Models (serialization des classificateurs)
        // doivent exister. (ils ne sont pas crees)
        PhonetiseurFacade pf = new PhonetiseurFacade(fichierSauvGraphemesPhonemesMatriceProba, repertoireARFF, repertoireSauvClassifieurs);

        /************************************************************/
        /*** Etape 2 (on fait soit l'etape 2-1, soit l'etape 2-2) ***/
        /************************************************************/
        // Etape 2-1 : on lance un apprentissage (en donnant "fichierMotsAApprendreDansLeDico" =>
        //             liste des mots que l'on veut apprendre dans le dico)
        pf.lancerApprentissage(pL, fichierMotsAApprendreDansLeDico, nomFichierProbasLangues);

        // Etape 2-2 : on charge les classifieurs. NE PAS oublier de faire cette etape si on souhaite
        //             phonetiser et si on n'a pas lance d'apprentissage.
        //             Inutile si on vient de lancer un apprentissage...
        pf.chargerClassifieurs();

        /*******************************/
        /*** Etape 3 (phonetisation) ***/
        /*******************************/
        float[] tProbasLangages = {-4.245247f, -8.048047f, -6.3719463f, -0.09644699f, -2.5825472f};
        System.out.println(pf.phonetiser("coucou", null, tProbasLangages));
    }
}
