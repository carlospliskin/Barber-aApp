import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

/// Datasource remoto — única responsabilidad: hablar con el backend.
/// No conoce nada de estado, storage ni navegación.
/// Retorna Map<String, dynamic> o lanza Exception.
class AuthRemoteDatasource {
  static const String _baseUrl = kDebugMode
      ? 'http://10.0.2.2:8080/graphql'    // Android emulator
      : 'https://tu-backend.com/graphql';  // Producción

  final http.Client _client;

  AuthRemoteDatasource({http.Client? client})
      : _client = client ?? http.Client();

  // ─── Login ─────────────────────────────────────────────────────────────────

  Future<Map<String, dynamic>> login({
    required String email,
    required String password,
  }) async {
    final result = await _mutate(
      mutation: '''
        mutation Login(\$email: String!, \$password: String!) {
          login(email: \$email, password: \$password) {
            $_authPayloadFields
          }
        }
      ''',
      variables: {'email': email, 'password': password},
    );
    return result['login'] as Map<String, dynamic>;
  }

  // ─── Registro barbería ──────────────────────────────────────────────────────

  Future<Map<String, dynamic>> registerOwner({
    required String email,
    required String password,
    required String barberiaName,
    required String ownerName,
    String? phone,
  }) async {
    final result = await _mutate(
      mutation: '''
        mutation Register(
          \$email: String!, \$password: String!,
          \$barberiaName: String!, \$ownerName: String!, \$phone: String
        ) {
          register(
            email: \$email, password: \$password,
            barberiaName: \$barberiaName, ownerName: \$ownerName, phone: \$phone
          ) {
            $_authPayloadFields
          }
        }
      ''',
      variables: {
        'email': email,
        'password': password,
        'barberiaName': barberiaName,
        'ownerName': ownerName,
        if (phone != null) 'phone': phone,
      },
    );
    return result['register'] as Map<String, dynamic>;
  }

  // ─── Registro cliente ──────────────────────────────────────────────────────

  Future<Map<String, dynamic>> registerClient({
    required String email,
    required String password,
    required String name,
    required String phone,
  }) async {
    final result = await _mutate(
      mutation: '''
        mutation RegisterClient(
          \$email: String!, \$password: String!,
          \$name: String!, \$phone: String!
        ) {
          registerClient(
            email: \$email, password: \$password,
            name: \$name, phone: \$phone
          ) {
            $_authPayloadFields
          }
        }
      ''',
      variables: {
        'email': email,
        'password': password,
        'name': name,
        'phone': phone,
      },
    );
    return result['registerClient'] as Map<String, dynamic>;
  }

  // ─── Switch mode ───────────────────────────────────────────────────────────

  Future<Map<String, dynamic>> switchMode({
    required String mode,
    required String token,
  }) async {
    final result = await _mutate(
      mutation: '''
        mutation SwitchMode(\$mode: String!) {
          switchMode(mode: \$mode) {
            $_authPayloadFields
          }
        }
      ''',
      variables: {'mode': mode},
      token: token,
    );
    return result['switchMode'] as Map<String, dynamic>;
  }

  // ─── Me (restaurar sesión) ─────────────────────────────────────────────────

  Future<Map<String, dynamic>> me({required String token}) async {
    final result = await _query(
      query: '''
        query {
          me {
            userId email name
            capabilities primaryRole
            tenant { id name timezone plan }
            clientProfile {
              id name phone isPenalized penaltyUntil noShowCount
            }
          }
        }
      ''',
      token: token,
    );
    return result['me'] as Map<String, dynamic>;
  }

  // ─── HTTP internos ─────────────────────────────────────────────────────────

  Future<Map<String, dynamic>> _query({
    required String query,
    Map<String, dynamic>? variables,
    String? token,
  }) =>
      _request(body: {'query': query, if (variables != null) 'variables': variables}, token: token);

  Future<Map<String, dynamic>> _mutate({
    required String mutation,
    Map<String, dynamic>? variables,
    String? token,
  }) =>
      _request(body: {'query': mutation, if (variables != null) 'variables': variables}, token: token);

  Future<Map<String, dynamic>> _request({
    required Map<String, dynamic> body,
    String? token,
  }) async {
    final response = await _client
        .post(
          Uri.parse(_baseUrl),
          headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
            if (token != null) 'Authorization': 'Bearer $token',
          },
          body: jsonEncode(body),
        )
        .timeout(const Duration(seconds: 15));

    if (response.statusCode != 200) {
      throw _HttpException(response.statusCode, response.body);
    }

    final json = jsonDecode(response.body) as Map<String, dynamic>;

    if (json.containsKey('errors')) {
      final errors = json['errors'] as List;
      final message =
          errors.isNotEmpty ? (errors.first as Map)['message'] as String : 'Error desconocido';
      throw _GraphQLException(message);
    }

    return json['data'] as Map<String, dynamic>;
  }

  // ─── Fields reutilizables ──────────────────────────────────────────────────

  static const _authPayloadFields = '''
    token refreshToken userId
    capabilities primaryRole
    tenant { id name timezone plan }
    clientProfile { id name phone isPenalized penaltyUntil noShowCount }
  ''';
}

// ─── Excepciones internas ─────────────────────────────────────────────────────

class _HttpException implements Exception {
  final int statusCode;
  final String body;
  _HttpException(this.statusCode, this.body);
  @override
  String toString() => 'HTTP $statusCode: $body';
}

class _GraphQLException implements Exception {
  final String message;
  _GraphQLException(this.message);
  @override
  String toString() => message;
}
