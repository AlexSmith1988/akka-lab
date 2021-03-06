import akka.actor.{ActorSystem, PoisonPill, Status}
import akka.testkit.{ImplicitSender, TestKit}
import shop.ShoppingCartActor._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import shop.ShoppingCartActor

class ShoppingCartActorSpec
  extends TestKit(ActorSystem("ShoppingCartActorSpec"))
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ImplicitSender {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "ShoppingCartActor" should {
    val shoppingItem = ShoppingItem("sku-000001", "Cheap headphones", 42.25, 2)

    "add an item to the shopping cart and preserve it after restart" in {
      val shoppingCartId = "sc-000001"
      val shoppingCartActor = system.actorOf(ShoppingCartActor.props(shoppingCartId))

      shoppingCartActor ! AddItemCommand(shoppingItem)
      expectMsg(AddItemResponse(shoppingItem))

      shoppingCartActor ! PoisonPill

      // creating a new actor with the same persistence id
      val shoppingCartActor2 = system.actorOf(ShoppingCartActor.props(shoppingCartId))

      shoppingCartActor2 ! GetItemsRequest

      expectMsg(GetItemsResponse(Seq(shoppingItem)))
    }

    "update an existing item to the shopping cart and preserve the changes after restart" in {
      val shoppingCartId = "sc-000002"
      val shoppingCartActor = system.actorOf(ShoppingCartActor.props(shoppingCartId))
      val updatedShoppingItem = shoppingItem.copy(quantity = 5)

      shoppingCartActor ! AddItemCommand(shoppingItem)
      expectMsg(AddItemResponse(shoppingItem))
      shoppingCartActor ! UpdateItemCommand(updatedShoppingItem)
      expectMsg(UpdateItemResponse(updatedShoppingItem))

      shoppingCartActor ! PoisonPill

      // creating a new actor with the same persistence id
      val shoppingCartActor2 = system.actorOf(ShoppingCartActor.props(shoppingCartId))
      shoppingCartActor2 ! GetItemsRequest

      expectMsg(GetItemsResponse(Seq(updatedShoppingItem)))
    }

    "remove an existing item from the shopping cart and preserve the changes after restart" in {
      val shoppingCartId = "sc-000003"
      val shoppingCartActor = system.actorOf(ShoppingCartActor.props(shoppingCartId))

      shoppingCartActor ! AddItemCommand(shoppingItem)
      expectMsg(AddItemResponse(shoppingItem))
      shoppingCartActor ! RemoveItemCommand(shoppingItem.id)
      expectMsg(RemoveItemResponse(shoppingItem.id))

      shoppingCartActor ! PoisonPill

      // creating a new actor with the same persistence id
      val shoppingCartActor2 = system.actorOf(ShoppingCartActor.props(shoppingCartId))
      shoppingCartActor2 ! GetItemsRequest

      expectMsg(GetItemsResponse(Seq.empty))
    }

    "return ItemNotFound if no items were added prior request" in {
          val shoppingCartId = "sc-000004"
          val shoppingCartActor = system.actorOf(ShoppingCartActor.props(shoppingCartId))

          shoppingCartActor ! GetItemsRequest
          expectMsg(Status.Failure(ItemNotFound))
        }
  }
}