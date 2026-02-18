package analyzer.visitors;

import analyzer.SemantiqueError;
import analyzer.ast.*;

import java.io.PrintWriter;
import java.util.*;

/**
 * Created: 19-01-10
 * Last Changed: 01-10-25
 * Author: Félix Brunet, Raphael Tremblay
 * <p>
 * Description: Ce visiteur explorer l'AST est renvois des erreurs lorsqu'une erreur sémantique est détectée.
 */

public class SemantiqueVisitor implements ParserVisitor {

    private final PrintWriter m_writer;

    private HashMap<String, VarType> SymbolTable = new HashMap<>(); // mapping variable -> type


    // variable pour les metrics
    public int VAR = 0;
    public int WHILE = 0;
    public int IF = 0;
    public int OP = 0;

    public SemantiqueVisitor(PrintWriter writer) {
        m_writer = writer;
    }

    /*
        IMPORTANT:
        *
        * L'implémentation des visiteurs se base sur la grammaire fournie (Grammaire.jjt). Il faut donc la consulter pour
        * déterminer les noeuds enfants à visiter. Cela vous sera utile pour lancer les erreurs au bon moment.
        * Pour chaque noeud, on peut :
        *   1. Déterminer le nombre d'enfants d'un noeud : jjtGetNumChildren()
        *   2. Visiter tous les noeuds enfants: childrenAccept()
        *   3. Accéder à un noeud enfant : jjtGetChild()
        *   4. Visiter un noeud enfant : jjtAccept()
        *   5. Accéder à m_value (type) ou m_ops (vecteur des opérateurs) selon la classe de noeud AST (src/analyser/ast)
        *
        * Cela permet d'analyser l'intégralité de l'arbre de syntaxe abstraite (AST) et d'effectuer une analyse sémantique du code.
        *
        * Le Visiteur doit lancer des erreurs lorsqu'une situation arrive.
        *
        * Pour vous aider, voici le code à utiliser pour lancer les erreurs :
        *
        * - Utilisation d'identifiant non défini :
        *   throw new SemantiqueError("Invalid use of undefined Identifier " + node.getValue());
        *
        * - Utilisation d'une variable non déclarée :
        *   throw new SemantiqueError(String.format("Variable %s was not declared", varName));
        *
        * - Plusieurs déclarations pour un identifiant. Ex : int a = 1; bool a = true; :
        *   throw new SemantiqueError(String.format("Identifier %s has multiple declarations", varName));
        *
        * - Utilisation d'un type numérique dans la condition d'un if ou d'un while :
        *   throw new SemantiqueError("Invalid type in condition");
        *
        * - Utilisation de types non valides pour des opérations de comparaison :
        *   throw new SemantiqueError("Invalid type in expression");
        *
        * - Assignation d'une valeur à une variable qui a déjà reçu une valeur d'un autre type. Ex : a = 1; a = true; :
        *   throw new SemantiqueError(String.format("Invalid type in assignation of Identifier %s", varName));
        *
        * - Les éléments d'une liste doivent être du même type. Ex : [1, 2, true] :
        *   throw new SemantiqueError("Invalid type in expression");
        * */


    @Override
    public Object visit(SimpleNode node, Object data) {
        return data;
    }

    @Override
    public Object visit(ASTProgram node, Object data) {
        node.childrenAccept(this, SymbolTable);
        m_writer.print(String.format("{VAR:%d, WHILE:%d, IF:%d, OP:%d}", this.VAR, this.WHILE, this.IF, this.OP));
        return null;
    }

    // Déclaration et assignation:
    // Le type déclaré (int/bool/float/list) est dans node.getValue() sur ASTDeclareStmt.
    // On doit vérifier que le type de l'expression à droite soit le même que le type déclaré.

    private static VarType declaredTypeFromString(String typeName) {
        if (typeName == null) return VarType.UNDEFINED;
        switch (typeName) {
            case "int": return VarType.INT;
            case "bool": return VarType.BOOL;
            case "float": return VarType.REAL;
            case "list": return VarType.LIST;
            default: return VarType.UNDEFINED;
        }
    }

    @Override
    public Object visit(ASTDeclareStmt node, Object data) {
        String varName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        HashMap<String, VarType> table = (HashMap<String, VarType>) data;

        if (table.containsKey(varName)) {
            throw new SemantiqueError(
                    String.format("Identifier %s has multiple declarations", varName)
            );
        }
        VarType declaredType = declaredTypeFromString(node.getValue());
        table.put(varName, declaredType);
        VAR++;
        node.jjtGetChild(0).jjtAccept(this, data);
        if (node.jjtGetNumChildren() > 1) {
            VarType initType = (VarType) node.jjtGetChild(1).jjtAccept(this, data);
            if (initType != VarType.UNDEFINED && initType != declaredType) {
                throw new SemantiqueError(
                        String.format("Invalid type in assignation of Identifier %s", varName));
            }
        }
        return null;
    }

    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        HashMap<String, VarType> table = (HashMap<String, VarType>) data;
        String varName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();

        if (!table.containsKey(varName)) {
            throw new SemantiqueError(
                    String.format("Variable %s was not declared", varName));
        }
        VarType varType = table.get(varName);
        VarType exprType = (VarType) node.jjtGetChild(1).jjtAccept(this, data);

        if (exprType != VarType.UNDEFINED && varType != exprType) {
            throw new SemantiqueError(
                    String.format(
                            "Invalid type in assignation of Identifier %s",
                            varName));
        }
        return null;
    }

    // les structures conditionnelle doivent vérifier que leur expression de condition est de type booléenne
    // On doit aussi compter les conditions dans les variables IF et WHILE
    // Elle sont aussi les seules structure avec des block qui devront garder leur déclaration locale.
    @Override
    public Object visit(ASTIfStmt node, Object data) {
        IF++;
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTIfCond node, Object data) {
        VarType cond = (VarType) node.jjtGetChild(0).jjtAccept(this, data);
        if (cond != VarType.BOOL) {
            throw new SemantiqueError("Invalid type in condition");
        }
        return null;
    }

    @Override
    public Object visit(ASTIfBlock node, Object data) {
        HashMap<String, VarType> localTable = new HashMap<>((HashMap<String, VarType>) data);
        node.childrenAccept(this, localTable);
        return null;
    }

    @Override
    public Object visit(ASTElseBlock node, Object data) {
        HashMap<String, VarType> localTable = new HashMap<>((HashMap<String, VarType>) data);
        node.childrenAccept(this, localTable);
        return null;
    }

    @Override
    public Object visit(ASTTernary node, Object data) {
        IF++;
        VarType cond = (VarType) node.jjtGetChild(0).jjtAccept(this, data);
        if (cond != VarType.BOOL) {
            throw new SemantiqueError("Invalid type in condition");
        }
        VarType t1 = (VarType) node.jjtGetChild(1).jjtAccept(this, data);
        VarType t2 = (VarType) node.jjtGetChild(2).jjtAccept(this, data);
        if (t1 != VarType.UNDEFINED && t2 != VarType.UNDEFINED && t1 != t2) {
            throw new SemantiqueError("Invalid type in expression");
        }
        return (t1 != VarType.UNDEFINED) ? t1 : t2;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        WHILE++;
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTWhileCond node, Object data) {
        VarType cond = (VarType) node.jjtGetChild(0).jjtAccept(this, data);
        if (cond != VarType.BOOL) {
            throw new SemantiqueError("Invalid type in condition");
        }
        return null;
    }

    @Override
    public Object visit(ASTWhileBlock node, Object data) {
        HashMap<String, VarType> localTable = new HashMap<>((HashMap<String, VarType>) data);
        node.childrenAccept(this, localTable);
        return null;
    }

    @Override
    public Object visit(ASTDoWhileStmt node, Object data) {
        HashMap<String, VarType> localTable = new HashMap<>((HashMap<String, VarType>) data);
        node.childrenAccept(this, localTable);
        return null;
    }

    @Override
    public Object visit(ASTCompExpr node, Object data) {
        /*
            Attention, ce noeud est plus complexe que les autres :
            - S’il n'a qu'un seul enfant, le noeud a pour type le type de son enfant.
            - S’il a plus d'un enfant, alors il s'agit d'une comparaison. Il a donc pour type "Bool".
            - Il n'est pas acceptable de faire des comparaisons de booléen avec les opérateurs < > <= >=.
            - Les opérateurs == et != peuvent être utilisé pour les nombres et les booléens, mais il faut que le type
            soit le même des deux côtés de l'égalité/l'inégalité.
        */
        if (node.jjtGetNumChildren() == 1) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }
        VarType left = (VarType) node.jjtGetChild(0).jjtAccept(this, data);
        VarType right = (VarType) node.jjtGetChild(1).jjtAccept(this, data);
        String op = node.getValue();
        if (left == VarType.UNDEFINED || right == VarType.UNDEFINED) {
            throw new SemantiqueError("Invalid type in expression");
        }
        if (op != null) {
            OP++;
        }
        if (op != null && (op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">="))) {
            if (!((left == VarType.INT || left == VarType.REAL) && (right == VarType.INT || right == VarType.REAL))) {
                throw new SemantiqueError("Invalid type in expression");
            }
        }
        if (op != null && (op.equals("==") || op.equals("!="))) {
            if (left != right) {
                throw new SemantiqueError("Invalid type in expression");
            }
        }
        return VarType.BOOL;
    }

    /*
        Opérateur à opérants multiples :
        - Il peuvent avoir de 2 à infinie noeuds enfants qui doivent tous être du même type que leur noeud parent
        - Par exemple, un AddExpr peux avoir une multiplication et un entier comme enfant, mais ne pourrait pas
        avoir une opération logique comme enfant.
        - Pour cette étapes il est recommandé de rédiger une function qui encapsule la visite des noeuds enfant
        et vérification de type
     */
    @Override
    public Object visit(ASTLogExpr node, Object data) {
        if (node.jjtGetNumChildren() > 1) {
            OP += node.jjtGetNumChildren() - 1;
        }
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            VarType t = (VarType) node.jjtGetChild(i).jjtAccept(this, data);
            if (t != VarType.BOOL) {
                throw new SemantiqueError("Invalid type in expression");
            }
        }
        return VarType.BOOL;
    }

    @Override
    public Object visit(ASTAddExpr node, Object data) {
        if (node.jjtGetNumChildren() > 1) {
            OP += node.jjtGetNumChildren() - 1;
        }
        VarType first = (VarType) node.jjtGetChild(0).jjtAccept(this, data);
        if (first != VarType.UNDEFINED && first != VarType.INT && first != VarType.REAL) {
            throw new SemantiqueError("Invalid type in expression");
        }
        for (int i = 1; i < node.jjtGetNumChildren(); i++) {
            VarType t = (VarType) node.jjtGetChild(i).jjtAccept(this, data);
            if (t != VarType.UNDEFINED && first != VarType.UNDEFINED && t != first) {
                throw new SemantiqueError("Invalid type in expression");
            }
        }
        return first;
    }

    @Override
    public Object visit(ASTMultExpr node, Object data) {
        if (node.jjtGetNumChildren() > 1) {
            OP += node.jjtGetNumChildren() - 1;
        }
        VarType first = (VarType) node.jjtGetChild(0).jjtAccept(this, data);
        if (first != VarType.UNDEFINED && first != VarType.INT && first != VarType.REAL) {
            throw new SemantiqueError("Invalid type in expression");
        }
        for (int i = 1; i < node.jjtGetNumChildren(); i++) {
            VarType t = (VarType) node.jjtGetChild(i).jjtAccept(this, data);
            if (t != VarType.UNDEFINED && first != VarType.UNDEFINED && t != first) {
                throw new SemantiqueError("Invalid type in expression");
            }
        }
        return first;
    }

    /*
        Opérateur unaire
        Les opérateurs unaires ont toujours un seul enfant.
    */
    @Override
    public Object visit(ASTNotExpr node, Object data) {
        OP++;
        VarType t = (VarType) node.jjtGetChild(0).jjtAccept(this, data);
        if (t != VarType.UNDEFINED && t != VarType.BOOL) {
            throw new SemantiqueError("Invalid type in expression");
        }
        return VarType.BOOL;
    }

    @Override
    public Object visit(ASTNegExpr node, Object data) {
        OP++;
        VarType t = (VarType) node.jjtGetChild(0).jjtAccept(this, data);
        if (t != VarType.UNDEFINED && t != VarType.INT && t != VarType.REAL) {
            throw new SemantiqueError("Invalid type in expression");
        }
        return t;
    }

    @Override
    public Object visit(ASTGenValue node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {
        if (!(node.jjtGetParent() instanceof ASTDeclareStmt)) {
            String varName = node.getValue();
            HashMap<String, VarType> table = (HashMap<String, VarType>) data;
            if (!table.containsKey(varName)) {
                throw new SemantiqueError(String.format("Variable %s was not declared", varName));
            }
        }
        VarType t = ((HashMap<String, VarType>) data).get(node.getValue());
        return t != null ? t : VarType.UNDEFINED;
    }

    @Override
    public Object visit(ASTBoolValue node, Object data) {
        return VarType.BOOL;
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        return VarType.INT;
    }

    @Override
    public Object visit(ASTRealValue node, Object data) {
        return VarType.REAL;
    }

    @Override
    public Object visit(ASTListExpr node, Object data) {
        if (node.jjtGetNumChildren() == 0) {
            return VarType.LIST;
        }
        VarType first = (VarType) node.jjtGetChild(0).jjtAccept(this, data);
        for (int i = 1; i < node.jjtGetNumChildren(); i++) {
            VarType t = (VarType) node.jjtGetChild(i).jjtAccept(this, data);
            if (t != VarType.UNDEFINED && first != VarType.UNDEFINED && t != first) {
                throw new SemantiqueError("Invalid type in expression");
            }
        }
        return VarType.LIST;
    }

    public enum VarType {
        UNDEFINED,
        INT,
        REAL,
        BOOL,
        LIST
    }


    private class DataStruct {
        public VarType type;

        public DataStruct() {
        }

        public DataStruct(VarType p_type) {
            type = p_type;
        }

    }
}
