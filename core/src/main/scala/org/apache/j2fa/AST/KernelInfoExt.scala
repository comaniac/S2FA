package org.apache.j2fa.AST

import scala.reflect.runtime.universe._
import scala.collection.mutable.Map

class KernelInfoExt(annot: String) extends Traverser {
  val valMap = Map[String, String]()
  val methodMap = Map[String, MethodInfo]()

  override def traverse(tree: Tree) = tree match {
    // Traverse class definition
    case pat @ ClassDef(mod, name, _, impl) =>
      val cName = name.toString
      val body = impl.body
      var hasKernel = false
      body.foreach(t => t match {
        case pat @ ClassDef(_, _, _, _) => // Nested classes
          traverse(t)
        case pat @ DefDef(_, _, _, _, _, _) => // Methods
          hasKernel = hasKernel | identifyKernel(cName, t)
        case _ =>
          // Do nothing
      })

      if (hasKernel == true) {
        body.foreach(t => {
          traverseDef(cName, t)
        })
      }

    case _ => super.traverse(tree)
  }

  def identifyKernel(prefix: String, tree: Tree): Boolean = tree match {
    case pat @ DefDef(mod, name, _, _, _, _) =>
      val mName = name.toString

      // Find the method with J2FA annotation
      val mConfig = mod.annotations.map(e => {
        val value = traverseAnnot(e)
        if (value.isEmpty == false)
          value.get
        else
          null
      }).find(e => e != null)

      // Record the method to be generated
      if (mConfig.isEmpty == false) {
        val newMethod = new MethodInfo(prefix, mName, mConfig.get)
        methodMap(prefix + "." + mName) = newMethod
        true
      }
      else
        false
     
    case _ =>
      false
  }

  def traverseDef(
      clazzName: String,
      tree: Tree
    ): Unit = tree match {

    // Method definition
    case pat @ DefDef(_, name, _, inArgs, outArg, rhs) =>
      val mName = name.toString
      val fullMethodName = clazzName + "." + mName

      // Only focus on J2FA kernel method(s)
      if (methodMap.contains(fullMethodName)) {
        val currMethod = methodMap.get(fullMethodName).get

        // Traverse input type parameters
        // Level: ValDef -> (AppliedTypeTree) -> Ident
        inArgs.foreach(arg => {
          assert (arg.length <= 1)
          if (arg.length == 1)
            traverseType(arg(0), currMethod)
        })

        // Traverse output type parameter
        // Level: (AppliedTypeTree) -> Ident
        traverseType(outArg, currMethod)

        // Traverse method body for local variables
        // Level: ValDef -> (AppliedTypeTree) -> Ident
        traverseBody(rhs, currMethod)
      }

    // Field definition
    case pat @ ValDef(_, name, typeTree, _) =>
      val vName = name.toString
      val newVal = new ValInfo(vName)
      traverseType(typeTree, null, newVal)
      valMap(vName) = newVal.getFullType
      
    case _ =>
      // Do nothing
  }

  def traverseBody(
      _tree: Tree,
      _method: MethodInfo
    ): Unit = _tree match {
    
    case pat @ Block(bodyList, _) =>
      bodyList.foreach(t => t match {
        case pat @ ValDef(_, name, typeTree, _) =>
          val vName = name.toString
          val newVal = new ValInfo(vName)
          traverseType(typeTree, _method, newVal)
          valMap(vName) = newVal.getFullType
        case _ =>
          // Do nothing
      })
      
    case _ =>
      // Do nothing
  }

  def traverseType(
      _tree: Tree, 
      _method: MethodInfo, 
      _val: ValInfo = null, 
      _type: TypeInfo = null
    ): Unit = _tree match {

    // Argument definition
    case pat @ ValDef(_, name, typeTree, _) =>
      val newVal = new ValInfo(name.toString)
      traverseType(typeTree, _method, newVal)
      assert(_method != null)
      _method.addArg(newVal)
      valMap(name.toString) = newVal.getFullType

    case pat @ TypeTree() =>
      // Do nothing

    case pat @ AppliedTypeTree(nameTree, gTypeTrees) =>
      // Extract type name
      var nameType = new TypeInfo
      traverseType(nameTree, _method, _val, nameType)

      val newType = new TypeInfo(nameType.getName)

      // Extract generic types (if any)
      gTypeTrees.foreach(e => {
        traverseType(e, _method, _val, newType)
      })
      if (_type != null) // Traversing generic type
        _type.addGenericType(newType)
      else { // Traversing noraml type
        if (_val == null) { // Method output type
          assert(_method != null)
          val newArg = new ValInfo
          newArg.setType(newType)
          _method.setOutput(newArg)
          valMap("j2faOut") = newArg.getFullType
        }
        else // Method argument or class field
          _val.setType(newType)
      }

    case pat @ Select(_, _) =>
      val name = getFullNameFromSelectTree(_tree)
      val newType = new TypeInfo(name)

      if (_type == null) { // Traversing normal type
        if (_val != null) // Method argument or class field
          _val.setType(newType)
        else { // Method output type
          assert(_method != null)
          val newArg = new ValInfo
          newArg.setType(newType)
          _method.setOutput(newArg)
          valMap("j2faOut") = newArg.getFullType
        }
      }
      else if (_type.getName != null)
        // Traversing generic type
        _type.addGenericType(newType)
      else
        // Other cases
        _type.setName(name)
     
    case pat @ Ident(name) => 
      val newType = new TypeInfo(name.toString)

      if (_type == null) { // Traversing normal type
        if (_val != null) // Method argument or class field
          _val.setType(newType)
        else { // Method output type
          assert(_method != null)
          val newArg = new ValInfo
          newArg.setType(newType)
          _method.setOutput(newArg)
        }
      }
      else if (_type.getName != null)
        // Traversing generic type
        _type.addGenericType(newType)
      else
        // Other cases
        _type.setName(name.toString)

    case _ => 
      println("Cannot match the AST of signature: ")
      println(showRaw(_tree))
  }

  def getFullNameFromSelectTree(tree: Tree): String = tree match {
    case pat @ Select(qul, name) =>
      getFullNameFromSelectTree(qul) + "/" + name.toString

    case pat @ Ident(name) =>
      name.toString
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

  def getVariables = valMap
 
  def getMethods = methodMap
}

