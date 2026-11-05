<?php

namespace App\Http\Controllers;

use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\ValidationException;

class DeviceTokenController extends Controller
{
    public function store(Request $request)
    {
        $data = $request->validate(['email' => 'required|email|max:254', 'password' => 'required|string|max:1024', 'device_name' => 'required|string|max:100']);
        $user = User::where('email', $data['email'])->first();
        if (! $user || ! Hash::check($data['password'], $user->password)) {
            throw ValidationException::withMessages(['email' => 'Credenciais inválidas.']);
        }
        $expires = now()->addHours(24);
        $token = $user->createToken($data['device_name'], ['catalog:read', 'practice:write'], $expires);

        return response()->json(['token' => $token->plainTextToken, 'expires_at' => $expires->toIso8601String(), 'user' => $user->only(['id', 'name', 'email', 'is_editor'])], 201)->header('Cache-Control', 'no-store');
    }

    public function destroy(Request $request)
    {
        $request->user()->currentAccessToken()->delete();

        return response()->noContent();
    }
}
