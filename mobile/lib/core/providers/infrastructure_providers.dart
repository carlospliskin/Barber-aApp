import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;

/// Providers de infraestructura compartidos entre features.
/// Viven en core/, no en ninguna feature específica.
/// Siguen DIP: las features dependen de estas abstracciones, no de implementaciones.

final httpClientProvider = Provider<http.Client>((_) => http.Client());

final secureStorageProvider = Provider<FlutterSecureStorage>((_) =>
  const FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
    iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock),
  ),
);
