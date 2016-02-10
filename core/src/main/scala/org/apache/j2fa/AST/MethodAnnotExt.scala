package org.apache.j2fa.AST

import scala.reflect.runtime.universe._

class MethodAnnotExt(annot: String) extends Traverser {
  var methodList = List[(String, String)]()

  override def traverse(tree: Tree) = tree match {
    case pat @ DefDef(mod, name, _, _, _, _) =>
      mod.annotations.foreach(e => {
        val value = traverseAnnot(e)
        if (value.isEmpty == false)
          methodList = methodList :+ (name.toString, value.get)
      })

    case _ => super.traverse(tree)
  }

  def traverseAnnot(tree: Tree): Option[String] = tree match {
    case pat @ Apply(nameTree, value) => 
      if (value.length == 1) {
        try {
          val name = nameTree.asInstanceOf[Select]
                      .qualifier.asInstanceOf[New].tpt.asInstanceOf[Ident].name
          if (name.toString.equals(annot))
            Some(value(0).toString)
          else
            None
        } catch {
          case _: Throwable => None
        }
      }
      else
        None

    case _ =>
      None
  }

  def getList = methodList
}

