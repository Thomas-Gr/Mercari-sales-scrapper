package utils

import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.Permission
import java.io.IOException

fun insertPermission(service: Drive, fileId: String) {
  val newPermission = Permission()
  newPermission.type = "anyone"
  newPermission.role = "reader"
  try {
    service.permissions().create(fileId, newPermission).execute()
    println("Granted read permission")
  } catch (e: IOException) {
    println("An error occurred: $e")
  }
}