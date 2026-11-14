import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;

class GraphQLService {
  // En producción esto viene de variables de entorno o flavor
  static const String _baseUrl = kDebugMode
      ? 'http://10.0.2.2:8080/graphql'   // Android emulator → localhost
      : 'https://tu-backend.com/graphql'; // Producción

  final http.Client _client;

  GraphQLService({http.Client? client})
      : _client = client ?? http.Client();

  Future<Map<String, dynamic>?> query({
    required String query,
    Map<String, dynamic>? variables,
    String? token,
  }) async {
    return _request(
      body: {'query': query, if (variables != null) 'variables': variables},
      token: token,
    );
  }

  Future<Map<String, dynamic>?> mutate({
    required String mutation,
    Map<String, dynamic>? variables,
    String? token,
  }) async {
    return _request(
      body: {'query': mutation, if (variables != null) 'variables': variables},
      token: token,
    );
  }

  Future<Map<String, dynamic>?> _request({
    required Map<String, dynamic> body,
    String? token,
  }) async {
    try {
      final headers = <String, String>{
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        if (token != null) 'Authorization': 'Bearer $token',
      };

      final response = await _client
          .post(
            Uri.parse(_baseUrl),
            headers: headers,
            body: jsonEncode(body),
          )
          .timeout(const Duration(seconds: 15));

      if (response.statusCode != 200) {
        throw Exception('HTTP ${response.statusCode}');
      }

      final json = jsonDecode(response.body) as Map<String, dynamic>;

      // Verificar errores de GraphQL
      if (json.containsKey('errors')) {
        final errors = json['errors'] as List;
        final message = errors.isNotEmpty
            ? (errors.first as Map)['message'] as String
            : 'Error desconocido';
        throw Exception(message);
      }

      return json['data'] as Map<String, dynamic>?;
    } on Exception {
      rethrow;
    } catch (e) {
      throw Exception('Error de red: $e');
    }
  }
}

final graphqlServiceProvider = Provider<GraphQLService>((ref) {
  return GraphQLService();
});
