2020-03-17 Christoph Knabe

# Differences between the article and the repository code

Thanks to Wiem for the constant effort to maintain the Medium article up-to-date.

I still found the following points in the article contradictory to the code:

* In section **1. Configuration**, subsection **1. Define Configuration module: the abstraction of the configuration API** there is still the 
  ```scala
  object Config {
    trait Service {
      val load: Task[AppConfig]
    }
  }
  ```
  although I do not find it in the project code.

* In section **1. Configuration**, subsection **2. Representation of the configuration dependency:** is written

   ```scala
   package configuration {
     type Configuration = zio.Has[Config.Service]
     ...
   }
   ```

   although in the project the same texts

   ```scala
   package object configuration {
     type Configuration = Has[ApiConfig] with Has[DbConfig]
     ...
   }
   ```

* At the end of the same subsection 2 the code of "database" does not appear in the project. If this is meant as a hypothetical example, I propose to express this more clearly by a subjunctive form:
  "If we would like to specify an effect that requires Configuration we could use this type alias."

* In section **1. Configuration**, subsection **3. Access the API :a helper that accesses the functions of the dependency:** is written

   ```scala
   package configuration {
     val load:ZIO[Configuration, Throwable, AppConfig] = 
     ZIO.accessM(_.get.load)
     ...
   }
   ```

   But in the project there is no value `load` in the `package object configuration`.

* In section **1. Configuration**, subsection **4. Implementation for Configuration using `ZLayer`** is written:
   The implementation of `load` using pureConfig would be:

   ```scala
   final class ConfigPrd  extends Config.Service {
     val load: Task[AppConfig] = Task.effect(loadConfigOrThrow[AppConfig])
   }
   ```

   `ZLayer` wraps the service `ConfigPrd` implementation:

   ```scala
   val live: ZLayer[Any, Nothing, Configuration] = ZLayer.succeed(new ConfigPrd)
   ```

   But in the project the value `live` is built as

   ```scala
   val live: Layer[Throwable, Configuration] = ZLayer.fromEffectMany(
     Task
     .effect(loadConfigOrThrow[AppConfig])
     .map(c => Has(c.api) ++ Has(c.dbConfig)))
   ```
* At the end of section **1. Configuration**, subsection **4. Implementation for Configuration using `ZLayer`** is written: `zio.provideLayer(ConfigPrd.live)`, although in the project in `object Main` the code is `provideSomeLayer[ZEnv](ConfigPrd.live ++ userPersistence)`.

* In the section **2. Database** in the subsection about function `mkTransactor` the return type is confusing. In the article it is a `Managed`, in the project there is no return type coded, but the IDE displays `ZManaged`. The difference between them is not clear.

* Some lines later in the article is cited `Managed(res).map(new UserPersistenceService(_))`, but in the project there is appended `.orDie`.

* Some lines later in the article is a big code block for function `live`. Some lines later the same function `live` appears in another variant. Unfortunately both do not agree with the code of funtion `live` in `object UserPersistenceService` in the project. The differences are e.g. in the return type. 
   In the article the second variant returns a `ZLayer[Configuration with Blocking, Nothing, UserPersistence]`, whereas in the project `live` returns a `ZLayer[Has[DbConfig] with Blocking, Throwable, UserPersistence]`. 
   In the article there is a line `config <- configuration.loadConfig.orDie.toManaged_`, whereas in the project `config <- configuration.dbConfig.toManaged_`.
   It would be good to copy the current implementation of `live` completely into the article.

* After that in the article is a 

   ```scala
   case class Test(users: Ref[Vector[User]]) extends Service
   ```

   which in the project has the class head

   ```scala
   case class TestUserDB(users: Ref[Vector[User]]) extends Persistence.Service[User]
   ```

* The function `delete` in this class is differently implemented. In the article as 

   ```scala
   def delete(id: Int): Task[Unit] =
     users.update(users => users.filterNot(_.id == id))
   ```

   In the project as

   ```scala
   def delete(id: Int): Task[Boolean] =
     users.modify(users => true -> users.filterNot(_.id == id))
   ```

* The following `ZLayer` build code from the article 

   ```scala
   def test(users: Ref[Vector[User]]): Layer[Nothing, UserPersistence] =
     ZLayer.fromEffect(Ref.make(Vector.empty[User]).map(Test))
   ```

   should also be replaced by the current implementation

   ```scala
   val layer: ZLayer[Any, Nothing, UserPersistence] =
     ZLayer.fromEffect(Ref.make(Vector.empty[User]).map(TestUserDB(_)))
   ```


* In section **3. Http Api** the HTTP routes are differently defined. In the article e.g.

  ```scala
  case GET -> Root / IntVar(id) => 
    getUser(id).foldM(_ => NotFound(), Ok(_))
  ```

  but in the project

  ```
  case GET -> Root / IntVar(id) => Ok(getUser(id))
  ```

  I prefer the form in the project, as it does not suppress other errors than "Not Found".

* In section **4. Interaction with the real World!** is defined

  ```scala
  type AppEnvironment = Configuration with Clock with UserPersistence
  ```

  whereas in the project it texts

  ```scala
  type AppEnvironment = Clock with Blocking with UserPersistence
  ```

* Also the next big codeblock in the definition of value `program` differs in some repects, e.g. `load` vs. `apiConfig`, `db.createTable *>` vs. `.provideSomeLayer[ZEnv](ConfigPrd.live ++ userPersistence)`

* After `val userPersistence` is written: `+` combines two layers, whereas in the code it seems that `++` is doing this.

* About `provideSomeLayer` is written in the article: "So we can use `provideSomeLayer[ZEnv]` in which you have to specify the dependencies minus `ZEnv`, in our case `Configuration` with `UserPersistence`", but in the code the dependencies provided are `ConfigPrd.live` and `userPersistence`.

* In the article the layer providing is written as

  ```scala
  val io = program.provideSomeLayer[ZEnv](ConfigPrd.live ++ userPersistence)
  ```

  which I prefer to the code in the project, where `.provideSomeLayer` is appended to a 15-line for-comprehension.

* Also the final exit status computation `io.fold(_ => 1, _ => 0)` deviates from the project code:

  ```scala
  program.foldM(
    err => putStrLn(s"Execution failed with: $err") *> IO.succeed(1),
    _ => IO.succeed(0)
  )
  ```

* In the **Tip** section all is congruent to the project code. 

May be some of the previously mentioned differences were intended by Wiem.