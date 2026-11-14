import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

class ClientRemoteDatasource {
  static const _baseUrl = kDebugMode
      ? 'http://10.0.2.2:8080/graphql'
      : 'https://api.barberia.app/graphql';

  // Fields reutilizables
  static const _publicServiceFields = '''
    id name category durationMinutes basePrice depositAmount depositType
  ''';

  static const _barberPublicFields = '''
    id name capacity avgRating
    availableSlots { time slotNumber isAvailable }
  ''';

  static const _barberiaPublicFields = '''
    id name isOpen avgRating reviewCount isFavorite
    services { $_publicServiceFields }
    barbers { $_barberPublicFields }
  ''';

  static const _appointmentFields = '''
    id confirmationCode
    client { id name phone }
    service { id name category durationMinutes basePrice depositAmount depositType }
    barberId barberName
    startTime endTime status depositStatus depositAmount
    rescheduleCount
  ''';

  final http.Client _client;
  ClientRemoteDatasource({http.Client? client})
      : _client = client ?? http.Client();

  // ─── Búsqueda de barberías ────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> searchBarberías({
    required String token,
    String? date,
  }) async {
    final data = await _query(
      '''query Search(\$date: String) {
        searchBarberías(date: \$date) { $_barberiaPublicFields }
      }''',
      variables: {'date': date},
      token: token,
    );
    return (data['searchBarberías'] as List? ?? [])
        .cast<Map<String, dynamic>>();
  }

  Future<Map<String, dynamic>?> getBarberiaDetail({
    required String barberiaId,
    required String token,
    String? date,
  }) async {
    final data = await _query(
      '''query Detail(\$id: String!, \$date: String) {
        barberiaDetail(barberiaId: \$id, date: \$date) { $_barberiaPublicFields }
      }''',
      variables: {'id': barberiaId, 'date': date},
      token: token,
    );
    return data['barberiaDetail'] as Map<String, dynamic>?;
  }

  // ─── Favoritos ────────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getFavorites({
    required String token,
  }) async {
    final data = await _query(
      '''query {
        myFavorites {
          id name avgRating reviewCount isOpen
        }
      }''',
      token: token,
    );
    return (data['myFavorites'] as List? ?? [])
        .cast<Map<String, dynamic>>();
  }

  Future<void> addFavorite({
    required String barberiaId,
    required String token,
  }) async {
    await _mutate(
      '''mutation(\$id: String!) { addFavorite(barberiaId: \$id) }''',
      variables: {'id': barberiaId},
      token: token,
    );
  }

  Future<void> removeFavorite({
    required String barberiaId,
    required String token,
  }) async {
    await _mutate(
      '''mutation(\$id: String!) { removeFavorite(barberiaId: \$id) }''',
      variables: {'id': barberiaId},
      token: token,
    );
  }

  // ─── Mis citas ────────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getMyAppointments({
    required String token,
  }) async {
    final data = await _query(
      '''query { myAppointments { $_appointmentFields } }''',
      token: token,
    );
    return (data['myAppointments'] as List? ?? [])
        .cast<Map<String, dynamic>>();
  }

  // ─── Crear reserva ────────────────────────────────────────────────────────

  Future<Map<String, dynamic>> createAppointment({
    required String serviceId,
    required String barberId,
    required String startTime,
    required String token,
  }) async {
    final data = await _mutate(
      '''mutation CreateBooking(\$input: CreateAppointmentInput!) {
        createAppointment(input: \$input) {
          appointmentId confirmationCode depositStatus paymentUrl
        }
      }''',
      variables: {
        'input': {
          'clientName':  '',    // el backend lo toma del JWT para clientes
          'clientPhone': '',
          'serviceId':   serviceId,
          'barberId':    barberId,
          'startTime':   startTime,
        },
      },
      token: token,
    );
    return data['createAppointment'] as Map<String, dynamic>;
  }

  // ─── Cancelar cita ────────────────────────────────────────────────────────

  Future<void> cancelAppointment({
    required String appointmentId,
    required String token,
  }) async {
    await _mutate(
      '''mutation(\$id: String!) { cancelAppointment(id: \$id) { canDo reason } }''',
      variables: {'id': appointmentId},
      token: token,
    );
  }

  // ─── Lealtad del cliente ──────────────────────────────────────────────────

  Future<Map<String, dynamic>?> getMyLoyalty({
    required String barberiaId,
    required String token,
  }) async {
    final data = await _query(
      '''query MyLoyalty(\$tenantId: String!) {
        myLoyalty(tenantId: \$tenantId) {
          id programId visitsCount totalVisitsEver lastVisitAt
        }
      }''',
      variables: {'tenantId': barberiaId},
      token: token,
    );
    return data['myLoyalty'] as Map<String, dynamic>?;
  }

  // ─── HTTP helpers ─────────────────────────────────────────────────────────

  Future<Map<String, dynamic>> _query(
    String q, {
    Map<String, dynamic?>? variables,
    required String token,
  }) => _send(
    {'query': q, if (variables != null) 'variables': variables
      ..removeWhere((_, v) => v == null)},
    token: token,
  );

  Future<Map<String, dynamic>> _mutate(
    String m, {
    Map<String, dynamic?>? variables,
    required String token,
  }) => _send(
    {'query': m, if (variables != null) 'variables': variables
      ..removeWhere((_, v) => v == null)},
    token: token,
  );

  Future<Map<String, dynamic>> _send(
    Map<String, dynamic> body, {
    required String token,
  }) async {
    final response = await _client
        .post(
          Uri.parse(_baseUrl),
          headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer $token',
          },
          body: jsonEncode(body),
        )
        .timeout(const Duration(seconds: 15));

    if (response.statusCode == 401) throw _UnauthorizedException();
    if (response.statusCode != 200) throw _HttpException(response.statusCode);

    final json = jsonDecode(response.body) as Map<String, dynamic>;
    if (json['errors'] != null) {
      final msg = (json['errors'] as List).first['message'] as String;
      throw _GraphQLException(msg);
    }
    return json['data'] as Map<String, dynamic>;
  }
}

class _HttpException implements Exception {
  final int code;
  _HttpException(this.code);
}
class _UnauthorizedException implements Exception {}
class _GraphQLException implements Exception {
  final String message;
  _GraphQLException(this.message);
  @override String toString() => message;
}
