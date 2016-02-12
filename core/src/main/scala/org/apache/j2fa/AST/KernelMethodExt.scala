package org.apache.j2fa.AST

import scala.reflect.runtime.universe._
import scala.collection.mutable.Map

class KernelMethodExt(annot: String) extends Traverser {
  val methodMap = Map[String, MethodInfo]()

  override def traverse(tree: Tree) = tree match {
    case pat @ DefDef(mod, name, _, inArgs, outArg, _) =>
      val mName = name.toString

      // Find the J2FA annotation
      val mConfig = mod.annotations.map(e => {
        val value = traverseAnnot(e)
        if (value.isEmpty == false)
          value.get
        else
          null
      }).find(e => e != null)

      // Only focus on J2FA kernel method(s)
      if (mConfig.isEmpty == false) {
        val newMethod = new MethodInfo(mName, mConfig.get)

        // Traverse input type parameters
        // Level: ValDef -> (AppliedTypeTree) -> Ident
        inArgs.foreach(arg => {
          assert (arg.length <= 1)
          if (arg.length == 1)
            traverseSig(arg(0), newMethod)
        })

        // Traverse output type parameter
        // Level: (AppliedTypeTree) -> Ident
        traverseSig(outArg, newMethod)

        // Append the method and finish extraction
        methodMap(mName) = newMethod
      }

      case _ => super.traverse(tree)
  }

  def traverseAnnot(tree: Tree): Option[Map[String, String]] = tree match {
    case pat @ Apply(nameTree, value) => 
      try {
        val name = nameTree.asInstanceOf[Select]
                    .qualifier.asInstanceOf[New].tpt.asInstanceOf[Ident].name
        if (name.toString.contains(annot)) {
          val annotValues = Map[String, String]()
          value.foreach(e => {
            val k = e.asInstanceOf[AssignOrNamedArg].lhs
                .asInstanceOf[Ident].name.toString.replace("\"", "")
            var v = e.asInstanceOf[AssignOrNamedArg].rhs
                .asInstanceOf[Literal].value.toString
            v = v.substring(v.indexOf("(") + 1, v.indexOf(")"))
            annotValues(k) = v
          })
          Some(annotValues)
        }
        else
          None
      } catch {
        case _: Throwable => 
          println("Cannot match the AST of annotations: Values")
          None
      }

    case _ =>
      println("Cannot match the AST of annotations: Apply")
      None
  }

  def traverseSig(
      _tree: Tree, 
      _method: MethodInfo, 
      _arg: ArgInfo = null, 
      _type: TypeInfo = null
    ): Unit = _tree match {
    case pat @ ValDef(_, name, typeTree, _) =>
      val newArg = new ArgInfo(name.toString)
      traverseSig(typeTree, _method, newArg)
      _method.addArg(newArg)

    case pat @ AppliedTypeTree(nameTree, gTypeTrees) =>
      // Extract type name
      var nameType = new TypeInfo
      traverseSig(nameTree, _method, _arg, nameType)

      val newType = new TypeInfo(nameType.getName)

      // Extract generic types (if any)
      gTypeTrees.foreach(e => {
        traverseSig(e, _method, _arg, newType)
      })
      if (_type != null) // Generic type
        _type.addGenericType(newType)
      else // Argument type
        _arg.setType(newType)

    case pat @ Ident(name) => 
      val newType = new TypeInfo(name.toString)

      if (_type == null) {
        if (_arg != null) // Argument type
          _arg.setType(newType)
        else { // Output type
          val newArg = new ArgInfo
          newArg.setType(newType)
          _method.setOutput(newArg)
        }
      }
      else if (_type.getName != null)
        // Generic type
        _type.addGenericType(newType)
      else
        // Other cases
        _type.setName(name.toString)

    case _ => 
      println("Cannot match the AST of signature")
  }
 
  def getMethods = methodMap
}

